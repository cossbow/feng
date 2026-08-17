package org.cossbow.feng.analysis.mono;

import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.gen.TypeParameter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a concrete (fully resolved) type instantiation discovered
 * by the Monomorphization pass.
 * <p>
 * Contains the {@link TypeDefinition} and the mapping from type parameters
 * to concrete type arguments, so downstream consumers (CGenerator) can
 * resolve all type variables without needing monoParams/monoResolve.
 */
public record ConcreteTypeInst(
        TypeDefinition def,
        Map<TypeParameter, TypeDeclarer> typeMap
) {
    public ConcreteTypeInst {
        typeMap = Collections.unmodifiableMap(new LinkedHashMap<>(typeMap));
    }
}
