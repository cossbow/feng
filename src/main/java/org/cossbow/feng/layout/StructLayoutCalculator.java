package org.cossbow.feng.layout;

/**
 * 模拟GCC内存对齐的计算工具
 */
public class StructLayoutCalculator {

    /**
     * 基本数据类型信息
     */
    public enum CType {
        CHAR(1, 1, "char"),
        SHORT(2, 2, "short"),
        INT(4, 4, "int"),
        LONG(4, 4, "long"),
        FLOAT(4, 4, "float"),
        DOUBLE(8, 8, "double"),
        INT64(8, 8, "int64_t");

        public final int size;
        public final int alignment;
        public final String name;

        CType(int size, int alignment, String name) {
            this.size = size;
            this.alignment = alignment;
            this.name = name;
        }

        public static CType fromString(String typeName) {
            return switch (typeName.toLowerCase()) {
                case "char" -> CHAR;
                case "short", "int16" -> SHORT;
                case "int", "int32", "bool" -> INT;
                case "long" -> LONG;
                case "float" -> FLOAT;
                case "double" -> DOUBLE;
                case "int64", "long long" -> INT64;
                default -> throw new IllegalArgumentException("Unknown type: " + typeName);
            };
        }
    }

    /**
     * 位域字段定义
     */
    public static class BitFieldMember {
        public final String name;
        public final CType type;      // 位域底层类型
        public final int bitWidth;    // 位宽
        public final boolean signed;  // 是否有符号

        public BitFieldMember(String name, CType type, int bitWidth, boolean signed) {
            this.name = name;
            this.type = type;
            this.bitWidth = bitWidth;
            this.signed = signed;

            if (bitWidth <= 0 || bitWidth > type.size * 8) {
                throw new IllegalArgumentException(
                        String.format("Bit width %d exceeds type %s capacity (%d bits)",
                                bitWidth, type.name, type.size * 8));
            }
        }

        @Override
        public String toString() {
            return String.format("%s %s : %d",
                    type.name, name, bitWidth);
        }
    }

    /**
     * 普通结构体字段
     */
    public static class StructMember {
        public final String name;
        public final CType type;
        public final int arraySize; // 0表示不是数组

        public StructMember(String name, CType type) {
            this(name, type, 0);
        }

        public StructMember(String name, CType type, int arraySize) {
            this.name = name;
            this.type = type;
            this.arraySize = arraySize;
        }

        @Override
        public String toString() {
            if (arraySize > 0) {
                return String.format("%s %s[%d]", type.name, name, arraySize);
            }
            return String.format("%s %s", type.name, name);
        }
    }

    /**
     * 计算出的字段布局信息
     */
    public static class MemberLayout {
        public final String name;
        public final int offset;      // 字节偏移
        public final int size;        // 字节大小
        public final int bitOffset;   // 位偏移（仅位域有效）
        public final int bitWidth;    // 位宽（仅位域有效）
        public final boolean isBitField;

        public MemberLayout(String name, int offset, int size) {
            this(name, offset, size, 0, 0, false);
        }

        public MemberLayout(String name, int offset, int size,
                            int bitOffset, int bitWidth, boolean isBitField) {
            this.name = name;
            this.offset = offset;
            this.size = size;
            this.bitOffset = bitOffset;
            this.bitWidth = bitWidth;
            this.isBitField = isBitField;
        }

        @Override
        public String toString() {
            if (isBitField) {
                return String.format("%-20s offset: %3d, bitOffset: %2d, bits: %2d",
                        name, offset, bitOffset, bitWidth);
            } else {
                return String.format("%-20s offset: %3d, size: %2d",
                        name, offset, size);
            }
        }
    }
}