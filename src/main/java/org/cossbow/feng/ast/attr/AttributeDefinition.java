package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.UniqueTable;
import org.cossbow.feng.ast.gen.TypeParameters;

import java.util.List;
import java.util.Optional;

public class AttributeDefinition extends TypeDefinition {
    private final UniqueTable<AttributeField> fields;

    public AttributeDefinition(Position pos,
                               Modifier modifier,
                               Identifier name,
                               UniqueTable<AttributeField> fields) {
        super(pos, modifier, Optional.of(name), TypeParameters.empty());
        this.fields = fields;
    }

    public UniqueTable<AttributeField> fields() {
        return fields;
    }
}
