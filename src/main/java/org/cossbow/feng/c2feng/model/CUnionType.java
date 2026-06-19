package org.cossbow.feng.c2feng.model;

import java.util.List;

/**
 * C union type: {@code union { fields }}
 */
public record CUnionType(String tagName, List<CField> fields, boolean isComplete) implements CType {
    @Override
    public String typeName() {
        return "union " + tagName;
    }
}
