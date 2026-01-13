package org.cossbow.feng.ast.struct;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

public class StructureDefinition extends TypeDefinition {
    private IdentifierTable<StructureField> fields;

    public StructureDefinition(Position pos,
                               Modifier modifier,
                               Identifier name,
                               TypeParameters generic,
                               TypeDomain domain,
                               IdentifierTable<StructureField> fields) {
        super(pos, modifier, name, generic, domain);
        this.fields = fields;
    }

    public IdentifierTable<StructureField> fields() {
        return fields;
    }

}
