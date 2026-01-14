package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final
public class PrimitiveDefinition extends TypeDefinition {
    private final Primitive primitive;

    public PrimitiveDefinition(Primitive primitive) {
        super(Position.ZERO, Modifier.empty(),
                new Symbol(Position.ZERO,
                        new Identifier(Position.ZERO, primitive.code)),
                TypeParameters.empty(), TypeDomain.PRIMITIVE);
        this.primitive = primitive;
    }

    public Primitive primitive() {
        return primitive;
    }

    static final Map<Primitive, PrimitiveDefinition> cache;

    static {
        cache = Arrays.stream(Primitive.values()).collect(
                Collectors.toUnmodifiableMap(Function.identity(),
                        PrimitiveDefinition::new));
    }
}
