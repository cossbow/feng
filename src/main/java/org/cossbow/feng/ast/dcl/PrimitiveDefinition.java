package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final
public class PrimitiveDefinition extends TypeDefinition {
    private final Primitive primitive;

    private PrimitiveDefinition(Primitive primitive) {
        super(Position.ZERO, Modifier.empty(),
                new Symbol(new Identifier(Position.ZERO, primitive.code)),
                TypeParameters.empty(), TypeDomain.PRIMITIVE);
        this.primitive = primitive;
    }

    public Primitive primitive() {
        return primitive;
    }

    //

    public static final Map<Primitive, PrimitiveDefinition> types;

    static {
        var map = new HashMap<Primitive, PrimitiveDefinition>();
        for (Primitive p : Primitive.values()) {
            var def = new PrimitiveDefinition(p);
            def.builtin(true);
            map.put(p, def);
        }
        types = Collections.unmodifiableMap(map);
    }
}
