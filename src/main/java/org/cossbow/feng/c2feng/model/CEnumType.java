package org.cossbow.feng.c2feng.model;

import java.util.List;

/**
 * C enum type: {@code enum { values }}
 * <p>
 * C enums are essentially {@code int} and can hold values outside
 * the declared constants. Therefore they are <em>not</em> mapped
 * to Fēng's type-safe {@code enum}; instead each constant is
 * emitted as a separate {@code const int}.
 */
public record CEnumType(String tagName, List<CEnumConstant> constants) implements CType {
    @Override
    public String typeName() {
        return "enum " + tagName;
    }
}
