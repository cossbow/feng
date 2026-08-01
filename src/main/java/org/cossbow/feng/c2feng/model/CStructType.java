package org.cossbow.feng.c2feng.model;

import java.util.List;

/**
 * C struct type: {@code struct { fields }}
 */
public record CStructType(String tagName, List<CField> fields,
                          boolean isComplete, boolean anonymous) implements CType {
}
