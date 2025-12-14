package org.cossbow.feng.ast.struct;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

public class StructureDefinition extends TypeDefinition {
    private final boolean union;
    private final IdentifierTable<StructureField> fields;

    public StructureDefinition(Position pos,
                               Modifier modifier,
                               Optional<Identifier> name,
                               TypeParameters generic,
                               boolean union,
                               IdentifierTable<StructureField> fields) {
        super(pos, modifier, name, generic);
        this.union = union;
        this.fields = fields;
    }

    public boolean union() {
        return union;
    }

    public IdentifierTable<StructureField> fields() {
        return fields;
    }

}
