package org.cossbow.feng.ast.struct;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.UniqueTable;

import java.util.Optional;

public class StructureDefinition extends TypeDefinition {
    private final boolean union;
    private final UniqueTable<StructureField> fields;

    public StructureDefinition(Position pos,
                               Modifier modifier,
                               Optional<Identifier> name,
                               TypeParameters generic,
                               boolean union,
                               UniqueTable<StructureField> fields) {
        super(pos, modifier, name, generic);
        this.union = union;
        this.fields = fields;
    }

    public boolean union() {
        return union;
    }

    public UniqueTable<StructureField> fields() {
        return fields;
    }

}
