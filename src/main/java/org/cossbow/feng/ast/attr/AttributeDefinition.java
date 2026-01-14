package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.gen.TypeParameters;

public class AttributeDefinition extends TypeDefinition {
    private IdentifierTable<AttributeField> fields;

    public AttributeDefinition(Position pos,
                               Modifier modifier,
                               Symbol symbol,
                               IdentifierTable<AttributeField> fields) {
        super(pos, modifier, symbol, TypeParameters.empty(),
                TypeDomain.ATTRIBUTE);
        this.fields = fields;
    }

    public IdentifierTable<AttributeField> fields() {
        return fields;
    }
}
