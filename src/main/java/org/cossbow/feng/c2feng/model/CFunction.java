package org.cossbow.feng.c2feng.model;

import java.util.List;

/**
 * A C function declaration.
 * <p>
 * In the metadata only the prototype is emitted (no function body).
 */
public record CFunction(
        String name,
        List<CField> parameters,
        CType returnType,
        boolean variadic,
        CLinkage linkage
) {
    /**
     * Whether the return type is {@code void}.
     */
    public boolean isReturnVoid() {
        return returnType instanceof CPrimitiveType pt && "void".equals(pt.name());
    }
}
