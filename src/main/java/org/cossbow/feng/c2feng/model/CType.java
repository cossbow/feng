package org.cossbow.feng.c2feng.model;

/**
 * Abstract base of the C type system.
 * <p>
 * All C types (primitives, pointers, arrays, structs, etc.) implement
 * this interface.
 */
public sealed interface CType
        permits CPrimitiveType, CPointerType, CArrayType,
                CStructType, CUnionType, CEnumType, CFunctionType {

    /**
     * A human-readable type name (for debugging and mapping).
     */
    String typeName();

    /**
     * Whether the type has a complete definition.
     * <p>
     * An incomplete type is one that has been forward-declared but
     * not yet defined (e.g. {@code struct S;} without a body).
     */
    default boolean isComplete() {
        return true;
    }
}
