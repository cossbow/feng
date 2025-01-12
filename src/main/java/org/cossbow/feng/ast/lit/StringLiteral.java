package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;

public class StringLiteral extends Literal {
    private final String value;

    public StringLiteral(Position pos, String value) {
        super(pos);
        this.value = value;
    }

    public String value() {
        return value;
    }
}
