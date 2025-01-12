package org.cossbow.feng.ast.struct;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

import java.util.List;
import java.util.Optional;

public class StructureDefinition extends TypeDefinition {
    private final boolean union;
    private final List<StructureMember> members;

    public StructureDefinition(Position pos,
                               Modifier modifier,
                               Optional<Identifier> name,
                               TypeParameters generic,
                               boolean union,
                               List<StructureMember> members) {
        super(pos, modifier, name, generic);
        this.union = union;
        this.members = members;
    }

    public boolean union() {
        return union;
    }

    public List<StructureMember> members() {
        return members;
    }

}
