package org.cossbow.feng.c2feng.model;

import java.util.Optional;

/**
 * A field of a C struct or union.
 * <p>
 * A bitfield has a non-empty {@code bitfieldWidth}; a regular
 * field uses {@code Optional.empty()}.
 */
public record CField(String name, CType type, Optional<Integer> bitfieldWidth) {
    public CField(String name, CType type) {
        this(name, type, Optional.empty());
    }
}
