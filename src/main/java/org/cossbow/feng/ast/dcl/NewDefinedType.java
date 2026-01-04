package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.DerivedType;

public class NewDefinedType extends NewType {
    private final DefinedType type;

    public NewDefinedType(Position pos,
                          DefinedType type) {
        super(pos);
        this.type = type;
    }

    public DefinedType type() {
        return type;
    }

    //

    @Override
    public String toString() {
        return type.toString();
    }
}
