package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Position;

public class PrimaryTypeExpression extends TypeExpression {
    private final DefinedType definedType;

    public PrimaryTypeExpression(Position pos,
                                 DefinedType definedType) {
        super(pos);
        this.definedType = definedType;
    }

    public DefinedType definedType() {
        return definedType;
    }
}
