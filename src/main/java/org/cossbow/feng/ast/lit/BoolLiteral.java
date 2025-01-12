package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;

public class BoolLiteral extends Literal {
    private final boolean value;

    public BoolLiteral(Position pos, boolean value) {
        super(pos);
        this.value = value;
    }

    public boolean value() {
        return value;
    }
}
