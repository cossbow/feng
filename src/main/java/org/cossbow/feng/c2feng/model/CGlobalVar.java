package org.cossbow.feng.c2feng.model;

import org.cossbow.feng.util.Optional;

/**
 * A C global variable declaration.
 * <p>
 * Its {@link CLinkage} determines whether it is exported, kept
 * file-internal, or skipped (extern).
 */
public record CGlobalVar(
        String name,
        CType type,
        boolean isConst,
        CLinkage linkage,
        Optional<String> externSource
) {
    public CGlobalVar(String name, CType type, boolean isConst, CLinkage linkage) {
        this(name, type, isConst, linkage, Optional.empty());
    }
}
