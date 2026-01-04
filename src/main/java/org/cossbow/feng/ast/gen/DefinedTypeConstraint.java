package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Position;

public class DefinedTypeConstraint extends TypeConstraint {
    private DefinedType definedType;

    public DefinedTypeConstraint(Position pos,
                                 DefinedType definedType) {
        super(pos);
        this.definedType = definedType;
    }

    public DefinedType definedType() {
        return definedType;
    }
}
