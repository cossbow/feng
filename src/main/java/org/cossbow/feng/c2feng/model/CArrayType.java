package org.cossbow.feng.c2feng.model;

import java.util.Optional;

/**
 * C array type:
 * <ul>
 *   <li>Fixed-length {@code int[10]} → {@code length=10}
 *   <li>Incomplete {@code int[]} / parameter decay → {@code length=empty}
 *       (degrades to {@code uint64})
 * </ul>
 */
public record CArrayType(CType elementType, Optional<Integer> length) implements CType {
    @Override
    public String typeName() {
        return length.map(l -> elementType.typeName() + "[" + l + "]")
                .orElseGet(() -> elementType.typeName() + "[]");
    }

    /**
     * Incomplete arrays (without a length) are treated as parameter
     * decay and are not considered complete types.
     */
    @Override
    public boolean isComplete() {
        return length.isPresent();
    }
}
