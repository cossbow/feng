package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.Optional;

public class AttributeDefinition extends TypeDefinition {
    private IdentifierTable<AttributeField> fields;

    public AttributeDefinition(Position pos,
                               Modifier modifier,
                               Identifier name,
                               IdentifierTable<AttributeField> fields) {
        super(pos, modifier, Optional.of(name), TypeParameters.empty());
        this.fields = fields;
    }

    public IdentifierTable<AttributeField> fields() {
        return fields;
    }
}
