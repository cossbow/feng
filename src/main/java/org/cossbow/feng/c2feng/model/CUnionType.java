package org.cossbow.feng.c2feng.model;

import java.util.List;

/**
 * C union type: {@code union { fields }}
 */
public record CUnionType(String tagName, List<CField> fields,
                         boolean isComplete, boolean anonymous,
                         boolean tagged) implements CType {

    public CUnionType(String tagName, List<CField> fields,
                      boolean isComplete, boolean anonymous) {
        this(tagName, fields, isComplete, anonymous, true);
    }
}
