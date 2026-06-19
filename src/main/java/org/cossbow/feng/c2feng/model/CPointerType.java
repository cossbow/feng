package org.cossbow.feng.c2feng.model;

/**
 * C pointer type: T*
 */
public record CPointerType(CType baseType, boolean isConst) implements CType {
    @Override
    public String typeName() {
        return (isConst ? "const " : "") + baseType.typeName() + "*";
    }

    /**
     * A pointer is always complete (even when it points to an incomplete type).
     */
    @Override
    public boolean isComplete() {
        return true;
    }
}
