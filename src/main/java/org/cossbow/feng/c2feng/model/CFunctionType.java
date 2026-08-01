package org.cossbow.feng.c2feng.model;

import java.util.List;

/**
 * C function pointer type: {@code T (*)(params)}
 * <p>
 * Mapped to {@code uint64} in the metadata (a raw address).
 */
public record CFunctionType(CType returnType, List<CType> paramTypes, boolean variadic) implements CType {
    @Override
    public boolean isComplete() {
        return true;
    }
}
