package org.cossbow.feng.c2feng.model;

/**
 * Linkage of C declarations.
 */
public enum CLinkage {
    /**
     * static — file-internal visibility, not exported.
     */
    STATIC,
    /**
     * Default global (no static / no extern) — visible to other
     * translation units, exported.
     */
    DEFAULT,
    /**
     * extern — references an external symbol; currently no entity emitted.
     */
    EXTERN,
}
