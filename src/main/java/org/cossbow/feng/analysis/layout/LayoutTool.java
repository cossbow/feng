package org.cossbow.feng.analysis.layout;

import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.analysis.SymbolContext;

import java.util.ArrayList;
import java.util.List;

import static org.cossbow.feng.util.ErrorUtil.*;

/**
 * 内存对齐计算器
 */
public class LayoutTool {
    private final SymbolContext context;

    // 位域状态跟踪
    private static class BitFieldUnit {
        Primitive type;       // 存储单元类型
        long unitOffset;       // 单元字节偏移
        long usedBits;         // 已使用位数
        long unitSize;         // 单元字节大小

        BitFieldUnit(Primitive type, long unitOffset) {
            this.type = type;
            this.unitOffset = unitOffset;
            this.usedBits = 0;
            this.unitSize = type.size();
        }

        boolean canFit(long bitWidth) {
            return usedBits + bitWidth <= unitSize * 8;
        }

        long currentOffset() {
            return unitOffset + unitSize;
        }
    }

    abstract static class LayoutBuilder {
        long pack; // 0表示使用默认对齐

        LayoutBuilder(long pack) {
            this.pack = pack;
        }

        long currentAlign(long align) {
            return pack > 0 ? Math.min(align, pack) : align;
        }

        /**
         * 计算对齐后的偏移
         */
        long alignOffset(long offset, long alignment) {
            return (offset + alignment - 1) & (-alignment);
        }
    }

    public LayoutTool(SymbolContext context) {
        this.context = context;
    }


    private StructureDefinition getType(DerivedTypeDeclarer dtd) {
        if (dtd.refer().has()) {
            return semantic("can't be reference: %s", dtd.pos());
        }
        if (!dtd.derivedType().generic().isEmpty()) {
            return unsupported("generic");
        }
        var def = context.findType(dtd.derivedType().symbol());
        if (def.none())
            return semantic("%s not defined: %s", dtd.derivedType(), dtd.pos());
        return (StructureDefinition) def.get();
    }

    private long getTypeAlign(TypeDeclarer type) {
        if (type instanceof PrimitiveTypeDeclarer ptd) {
            return ptd.primitive().align();
        } else if (type instanceof ArrayTypeDeclarer atd) {
            return getTypeAlign(atd.element());
        } else if (type instanceof DerivedTypeDeclarer dtd) {
            var def = getType(dtd);
            return def.layout().must().align();
        }
        return unreachable();
    }

    private long getTypeSize(TypeDeclarer type) {
        if (type instanceof PrimitiveTypeDeclarer ptd) {
            return ptd.primitive().size();
        } else if (type instanceof ArrayTypeDeclarer atd) {
            return atd.len() * getTypeSize(atd.element());
        } else if (type instanceof DerivedTypeDeclarer dtd) {
            var def = getType(dtd);
            return def.layout().must().size();
        }
        return unreachable();
    }


    private class StructLayoutBuilder extends LayoutBuilder {
        private long currentOffset = 0;
        private long maxAlign = 1;
        private final List<MemberLayout> layout = new ArrayList<>();

        private BitFieldUnit currentBitFieldUnit = null;

        StructLayoutBuilder(long pack) {
            super(pack);
        }

        public void addMember(Primitive type) {
            addMember(type.size(), type.align());
        }

        /**
         * 添加普通字段
         */
        public void addMember(long size, long align) {
            // 结束当前位域单元
            if (currentBitFieldUnit != null) {
                currentOffset = currentBitFieldUnit.currentOffset();
                currentBitFieldUnit = null;
            }

            long alignment = currentAlign(align);

            // 对齐当前偏移
            currentOffset = alignOffset(currentOffset, alignment);

            layout.add(new MemberLayout(currentOffset, size));

            // 更新状态
            currentOffset += size;
            maxAlign = Math.max(maxAlign, alignment);
        }

        /**
         * 添加位域字段
         */
        public void addBitField(Primitive type, long bits) {
            var align = currentAlign(type.align());

            // 检查是否需要新存储单元
            if (currentBitFieldUnit == null ||
                    currentBitFieldUnit.type != type ||
                    !currentBitFieldUnit.canFit(bits)) {

                // 对齐到新单元
                if (currentBitFieldUnit != null) {
                    currentOffset = currentBitFieldUnit.currentOffset();
                }

                currentOffset = alignOffset(currentOffset, align);
                currentBitFieldUnit = new BitFieldUnit(type, currentOffset);
            }

            // 添加位域
            layout.add(new MemberLayout(
                    currentBitFieldUnit.unitOffset,
                    currentBitFieldUnit.unitSize,
                    currentBitFieldUnit.usedBits,
                    bits, true
            ));

            currentBitFieldUnit.usedBits += bits;
            maxAlign = Math.max(maxAlign, align);
        }

        /**
         * 添加无名位域（填充）
         */
        public void addAnonymousBitField(Primitive type, int bits) {
            if (bits == 0) {
                // 零宽度位域：强制新存储单元
                if (currentBitFieldUnit != null) {
                    currentOffset = currentBitFieldUnit.currentOffset();
                    currentBitFieldUnit = null;
                }
            } else {
                // 普通无名位域
                addBitField(type, bits);
            }
        }

        private void calcFields(Iterable<StructureField> fields) {
            for (var sf : fields) {
                calcField(sf);
            }
        }

        private void calcField(StructureField sf) {
            if (sf.bitfield().has()) {
                if (!(sf.type() instanceof PrimitiveTypeDeclarer ptd)) {
                    semantic("bitfield only set for primitive: %s",
                            sf.type().pos());
                    return;
                }
                addBitField(ptd.primitive(), sf.bits());
                return;
            }

            var size = getTypeSize(sf.type());
            var align = getTypeAlign(sf.type());
            addMember(size, align);
        }

        /**
         * 获取最终布局和总大小
         */
        StructureLayout toLayout() {
            // 处理最后一个位域单元
            if (currentBitFieldUnit != null) {
                currentOffset = currentBitFieldUnit.currentOffset();
            }

            // 结构体总大小对齐
            var totalSize = alignOffset(currentOffset, maxAlign);

            return new StructureLayout(List.copyOf(layout), totalSize, maxAlign);
        }

    }

    private StructureLayout structLayout(StructureDefinition sd) {
        var builder = new StructLayoutBuilder(sd.pack());
        builder.calcFields(sd.fields());
        return builder.toLayout();
    }


    //

    private class UnionLayoutBuilder extends LayoutBuilder {
        UnionLayoutBuilder(long pack) {
            super(pack);
        }

        private long unionAlign(List<StructureField> fields) {
            long maxAlign = 1;
            for (var field : fields) {
                long align = currentAlign(getTypeAlign(field.type()));
                maxAlign = Math.max(maxAlign, align);
            }
            return maxAlign;
        }

        private long unionSize(List<StructureField> fields,
                               long align) {
            long maxSize = 0;
            for (var field : fields) {
                var size = getTypeSize(field.type());
                maxSize = Math.max(maxSize, size);
            }
            // 对齐到联合体的对齐值
            return alignOffset(maxSize, align);
        }

        public StructureLayout unionLayout(List<StructureField> fields) {
            var unionAlign = unionAlign(fields);
            var unionSize = unionSize(fields, unionAlign);

            var layouts = new ArrayList<MemberLayout>();
            for (var field : fields) {
                var size = getTypeSize(field.type());
                layouts.add(new MemberLayout(0, size));
            }
            return new StructureLayout(layouts, unionSize, unionAlign);
        }

    }


    private StructureLayout unionLayout(StructureDefinition sd) {
        var builder = new UnionLayoutBuilder(sd.pack());
        return builder.unionLayout(sd.fields().values());
    }


    public StructureLayout buildLayout(StructureDefinition sd) {
        return switch (sd.domain()) {
            case STRUCT -> structLayout(sd);
            case UNION -> unionLayout(sd);
            case null, default -> unreachable();
        };
    }

}
