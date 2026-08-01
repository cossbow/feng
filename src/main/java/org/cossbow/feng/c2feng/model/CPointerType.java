package org.cossbow.feng.c2feng.model;

/**
 * C pointer type: T*
 */
public record CPointerType(CType baseType, boolean isConst) implements CType {
    /**
     * A pointer is always complete (even when it points to an incomplete type).
     */
    @Override
    public boolean isComplete() {
        return true;
    }
}
