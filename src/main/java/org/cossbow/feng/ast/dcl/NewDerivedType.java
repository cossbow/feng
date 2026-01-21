package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.DerivedType;

public class NewDerivedType extends NewType {
    private final DerivedType type;

    public NewDerivedType(Position pos,
                          DerivedType type) {
        super(pos);
        this.type = type;
    }

    public DerivedType type() {
        return type;
    }

    //

    @Override
    public String toString() {
        return type.toString();
    }
}
