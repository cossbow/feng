package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.gen.TypeParameters;

import static org.cossbow.feng.ast.Position.ZERO;

public class AttributeDefinition extends TypeDefinition {
    private IdentifierMap<AttributeField> fields;

    public AttributeDefinition(Position pos,
                               Modifier modifier,
                               Symbol symbol,
                               IdentifierMap<AttributeField> fields) {
        super(pos, modifier, symbol, TypeParameters.empty(),
                TypeDomain.ATTRIBUTE);
        this.fields = fields;
    }

    public IdentifierMap<AttributeField> fields() {
        return fields;
    }

    //

    public static final Identifier InheritName = new Identifier("Inherit");
    public static final
    AttributeDefinition InheritDef = new AttributeDefinition(ZERO,
            Modifier.empty(), new Symbol(InheritName),
            new IdentifierMap<>());

    static {
        InheritDef.builtin(true);
    }
}
