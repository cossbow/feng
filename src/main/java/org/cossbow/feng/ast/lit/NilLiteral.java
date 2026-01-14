package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;

public class NilLiteral extends Literal {
    public NilLiteral(Position pos) {
        super(pos);
    }

    @Override
    public String type() {
        return "nil";
    }

    @Override
    public String toString() {
        return "nil";
    }
}
