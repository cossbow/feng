package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.Optional;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final
public class PrimitiveDefinition extends TypeDefinition {
    private final Primitive primitive;

    public PrimitiveDefinition(Primitive primitive) {
        super(Position.ZERO, Modifier.empty(),
                Optional.of(
                        new Identifier(Position.ZERO, primitive.code)),
                TypeParameters.empty());
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
