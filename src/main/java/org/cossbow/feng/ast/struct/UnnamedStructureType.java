package org.cossbow.feng.ast.struct;

import org.cossbow.feng.ast.Position;

public class UnnamedStructureType extends StructureType {
    private StructureDefinition definition;

    public UnnamedStructureType(Position pos,
                                StructureDefinition definition) {
        super(pos);
        this.definition = definition;
    }

    public StructureDefinition definition() {
        return definition;
    }
}
