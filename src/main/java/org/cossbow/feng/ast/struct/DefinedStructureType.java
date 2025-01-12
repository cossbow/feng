package org.cossbow.feng.ast.struct;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.DefinedType;

public class DefinedStructureType extends StructureType {
    private final DefinedType type;

    public DefinedStructureType(Position pos,
                                DefinedType type) {
        super(pos);
        this.type = type;
    }

    public DefinedType type() {
        return type;
    }
}
