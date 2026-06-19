package org.cossbow.feng.c2feng.model;

import java.util.List;

/**
 * C struct type: {@code struct { fields }}
 */
public record CStructType(String tagName, List<CField> fields, boolean isComplete) implements CType {
    @Override
    public String typeName() {
        return "struct " + tagName;
    }
}
