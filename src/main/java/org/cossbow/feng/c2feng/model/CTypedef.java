package org.cossbow.feng.c2feng.model;

import org.cossbow.feng.util.Optional;

/**
 * A C typedef — a type alias that is transparently expanded.
 * <p>
 * No standalone Fēng type alias is generated; the typedef is only
 * recorded for type-resolution during conversion.
 */
public record CTypedef(String name, CType underlyingType) {
}
