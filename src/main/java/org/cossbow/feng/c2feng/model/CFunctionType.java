package org.cossbow.feng.c2feng.model;

import java.util.List;

/**
 * C function pointer type: {@code T (*)(params)}
 * <p>
 * Mapped to {@code uint64} in the metadata (a raw address).
 */
public record CFunctionType(CType returnType, List<CType> paramTypes, boolean variadic) implements CType {
    @Override
    public String typeName() {
        var sb = new StringBuilder();
        sb.append(returnType.typeName()).append("(*)(");
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(paramTypes.get(i).typeName());
        }
        if (variadic) {
            if (!paramTypes.isEmpty()) sb.append(", ");
            sb.append("...");
        }
        sb.append(')');
        return sb.toString();
    }

    @Override
    public boolean isComplete() {
        return true;
    }
}
