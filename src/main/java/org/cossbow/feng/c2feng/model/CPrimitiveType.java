package org.cossbow.feng.c2feng.model;

/**
 * C primitive types: void, char, int, double, etc.
 */
public record CPrimitiveType(String name) implements CType {

    /**
     * Predefined constants for common C primitive types.
     */
    public static final CPrimitiveType VOID = new CPrimitiveType("void");
    public static final CPrimitiveType CHAR = new CPrimitiveType("char");
    public static final CPrimitiveType INT = new CPrimitiveType("int");
    public static final CPrimitiveType UINT = new CPrimitiveType("unsigned int");
    public static final CPrimitiveType SHORT = new CPrimitiveType("short");
    public static final CPrimitiveType USHORT = new CPrimitiveType("unsigned short");
    public static final CPrimitiveType LONG = new CPrimitiveType("long");
    public static final CPrimitiveType ULONG = new CPrimitiveType("unsigned long");
    public static final CPrimitiveType LONGLONG = new CPrimitiveType("long long");
    public static final CPrimitiveType FLOAT = new CPrimitiveType("float");
    public static final CPrimitiveType DOUBLE = new CPrimitiveType("double");
    public static final CPrimitiveType BOOL = new CPrimitiveType("_Bool");
    public static final CPrimitiveType SIZEOF_T = new CPrimitiveType("size_t");

    @Override
    public String typeName() {
        return name;
    }
}
