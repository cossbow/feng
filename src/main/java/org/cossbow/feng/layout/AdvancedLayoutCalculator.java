package org.cossbow.feng.layout;

import java.util.ArrayList;
import java.util.List;

import static org.cossbow.feng.layout.StructLayoutCalculator.*;

/**
 * 支持嵌套结构体和联合
 */
public class AdvancedLayoutCalculator {

    /**
     * 结构体定义
     */
    public static class StructDef {
        String name;
        List<Object> members; // 可以是StructMember, BitFieldMember或StructDef
        int packValue = 0;

        public StructDef(String name) {
            this.name = name;
            this.members = new ArrayList<>();
        }

        public StructDef addMember(Object member) {
            members.add(member);
            return this;
        }

        public StructDef setPack(int packValue) {
            this.packValue = packValue;
            return this;
        }
    }

    /**
     * 计算嵌套结构体布局
     */
    public static AlignmentCalculator.StructLayout calculateLayout(StructDef structDef) {
        AlignmentCalculator calc = new AlignmentCalculator(structDef.packValue);
        processMembers(calc, structDef.members);
        return calc.getLayout();
    }

    private static void processMembers(AlignmentCalculator calc, List<Object> members) {
        for (Object member : members) {
            if (member instanceof StructMember) {
                calc.addMember((StructMember) member);
            } else if (member instanceof BitFieldMember) {
                calc.addBitField((BitFieldMember) member);
            } else if (member instanceof StructDef) {
                // 嵌套结构体：先计算内部结构体布局
                AlignmentCalculator.StructLayout innerLayout =
                        calculateLayout((StructDef) member);

                // 添加整个结构体作为成员
                calc.addMember(new StructMember(
                        ((StructDef) member).name,
                        CType.fromString("char"), // 使用char数组模拟
                        innerLayout.totalSize
                ));
            }
        }
    }

    /**
     * 联合体布局计算
     */
    public static class UnionLayout {
        public final List<MemberLayout> members;
        public final int totalSize;
        public final int alignment;

        public UnionLayout(List<StructMember> members, int packValue) {
            this.alignment = calculateUnionAlignment(members, packValue);
            this.totalSize = calculateUnionSize(members, packValue, alignment);
            this.members = calculateUnionMemberLayouts(members, packValue, totalSize);
        }

        private int calculateUnionAlignment(List<StructMember> members,
                                            int packValue) {
            int maxAlign = 1;
            for (StructMember member : members) {
                int align = packValue > 0 ?
                        Math.min(member.type.alignment, packValue) :
                        member.type.alignment;
                maxAlign = Math.max(maxAlign, align);
            }
            return maxAlign;
        }

        private int calculateUnionSize(List<StructMember> members,
                                       int packValue, int alignment) {
            int maxSize = 0;
            for (StructMember member : members) {
                int size = member.arraySize > 0 ?
                        member.type.size * member.arraySize :
                        member.type.size;
                maxSize = Math.max(maxSize, size);
            }

            // 对齐到联合体的对齐值
            return (maxSize + alignment - 1) & ~(alignment - 1);
        }

        private List<MemberLayout> calculateUnionMemberLayouts(
                List<StructMember> members,
                int packValue, int totalSize) {

            var layouts = new ArrayList<MemberLayout>();
            for (StructMember member : members) {
                int size = member.arraySize > 0 ?
                        member.type.size * member.arraySize :
                        member.type.size;
                layouts.add(new MemberLayout(member.name, 0, size));
            }
            return layouts;
        }

        public void printLayout() {
            System.out.println("=== Union Layout ===");
            System.out.printf("Total size: %d bytes, Alignment: %d\n",
                    totalSize, alignment);
            System.out.println("-".repeat(50));

            for (var member : members) {
                System.out.printf("%-20s offset: %3d, size: %2d\n",
                        member.name, member.offset, member.size);
            }
        }
    }
}