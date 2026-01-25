package org.cossbow.feng.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.cossbow.feng.layout.StructLayoutCalculator.*;

/**
 * GCC内存对齐计算器
 */
public class AlignmentCalculator {
    private int currentOffset = 0;
    private int maxAlignment = 1;
    private int packValue = 0; // 0表示使用默认对齐
    private final List<MemberLayout> layout = new ArrayList<>();

    // 位域状态跟踪
    private static class BitFieldUnit {
        CType type;           // 存储单元类型
        int unitOffset;       // 单元字节偏移
        int usedBits;         // 已使用位数
        int unitSize;         // 单元字节大小

        BitFieldUnit(CType type, int unitOffset) {
            this.type = type;
            this.unitOffset = unitOffset;
            this.usedBits = 0;
            this.unitSize = type.size;
        }

        boolean canFit(int bitWidth) {
            return usedBits + bitWidth <= unitSize * 8;
        }
    }

    private BitFieldUnit currentBitFieldUnit = null;

    public AlignmentCalculator() {
        this(0); // 默认对齐
    }

    public AlignmentCalculator(int packValue) {
        this.packValue = packValue;
    }

    /**
     * 计算对齐后的偏移
     */
    private int alignOffset(int offset, int alignment) {
        if (packValue > 0) {
            alignment = Math.min(alignment, packValue);
        }
        return (offset + alignment - 1) & ~(alignment - 1);
    }

    /**
     * 添加普通字段
     */
    public void addMember(StructMember member) {
        // 结束当前位域单元
        if (currentBitFieldUnit != null) {
            currentOffset = currentBitFieldUnit.unitOffset + currentBitFieldUnit.unitSize;
            currentBitFieldUnit = null;
        }

        int alignment = packValue > 0 ?
                Math.min(member.type.alignment, packValue) :
                member.type.alignment;

        // 对齐当前偏移
        currentOffset = alignOffset(currentOffset, alignment);

        // 计算大小
        int size = member.arraySize > 0 ?
                member.type.size * member.arraySize :
                member.type.size;

        layout.add(new MemberLayout(
                member.name, currentOffset, size
        ));

        // 更新状态
        currentOffset += size;
        maxAlignment = Math.max(maxAlignment, alignment);
    }

    /**
     * 添加位域字段
     */
    public void addBitField(BitFieldMember bitField) {
        int alignment = packValue > 0 ?
                Math.min(bitField.type.alignment, packValue) :
                bitField.type.alignment;

        // 检查是否需要新存储单元
        if (currentBitFieldUnit == null ||
                currentBitFieldUnit.type != bitField.type ||
                !currentBitFieldUnit.canFit(bitField.bitWidth)) {

            // 对齐到新单元
            if (currentBitFieldUnit != null) {
                currentOffset = currentBitFieldUnit.unitOffset + currentBitFieldUnit.unitSize;
            }

            currentOffset = alignOffset(currentOffset, alignment);
            currentBitFieldUnit = new BitFieldUnit(bitField.type, currentOffset);
        }

        // 添加位域
        layout.add(new MemberLayout(
                bitField.name,
                currentBitFieldUnit.unitOffset,
                currentBitFieldUnit.unitSize,
                currentBitFieldUnit.usedBits,
                bitField.bitWidth,
                true
        ));

        currentBitFieldUnit.usedBits += bitField.bitWidth;
        maxAlignment = Math.max(maxAlignment, alignment);
    }

    /**
     * 添加无名位域（填充）
     */
    public void addAnonymousBitField(CType type, int bitWidth) {
        if (bitWidth == 0) {
            // 零宽度位域：强制新存储单元
            if (currentBitFieldUnit != null) {
                currentOffset = currentBitFieldUnit.unitOffset + currentBitFieldUnit.unitSize;
                currentBitFieldUnit = null;
            }
        } else {
            // 普通无名位域
            addBitField(new BitFieldMember("(anonymous)", type, bitWidth, false));
        }
    }

    /**
     * 获取最终布局和总大小
     */
    public StructLayout getLayout() {
        // 处理最后一个位域单元
        if (currentBitFieldUnit != null) {
            currentOffset = currentBitFieldUnit.unitOffset + currentBitFieldUnit.unitSize;
        }

        // 结构体总大小对齐
        int totalSize = alignOffset(currentOffset, maxAlignment);

        return new StructLayout(
                new ArrayList<>(layout),
                totalSize,
                maxAlignment,
                packValue
        );
    }

    /**
     * 布局结果
     */
    public static class StructLayout {
        public final List<MemberLayout> members;
        public final int totalSize;
        public final int maxAlignment;
        public final int packValue;

        public StructLayout(List<MemberLayout> members, int totalSize,
                            int maxAlignment, int packValue) {
            this.members = members;
            this.totalSize = totalSize;
            this.maxAlignment = maxAlignment;
            this.packValue = packValue;
        }

        public void printLayout() {
            System.out.println("=== Struct Layout ===");
            System.out.printf("Total size: %d bytes, Alignment: %d",
                    totalSize, maxAlignment);
            if (packValue > 0) {
                System.out.printf(" (packed: %d)", packValue);
            }
            System.out.println();
            System.out.println("-".repeat(50));

            for (MemberLayout member : members) {
                System.out.println(member);
            }

            // 显示内存布局图
            printMemoryLayout();
        }

        private void printMemoryLayout() {
            System.out.println("\nMemory layout:");
            byte[] memory = new byte[totalSize];
            Arrays.fill(memory, (byte) 0xCC); // 填充未初始化数据

            for (MemberLayout member : members) {
                if (!member.isBitField) {
                    for (int i = 0; i < member.size; i++) {
                        int pos = member.offset + i;
                        if (pos < memory.length) {
                            memory[pos] = (byte) (0xAA + (i % 16)); // 标记字段
                        }
                    }
                }
            }

            // 打印十六进制视图
            for (int i = 0; i < memory.length; i += 16) {
                System.out.printf("%04X: ", i);

                // 十六进制
                for (int j = 0; j < 16; j++) {
                    if (i + j < memory.length) {
                        System.out.printf("%02X ", memory[i + j]);
                    } else {
                        System.out.print("   ");
                    }
                    if (j == 7) System.out.print(" ");
                }

                System.out.print(" | ");

                // ASCII
                for (int j = 0; j < 16 && i + j < memory.length; j++) {
                    byte b = memory[i + j];
                    char c = (b >= 32 && b < 127) ? (char) b : '.';
                    System.out.print(c);
                }

                System.out.println();
            }
        }
    }
}