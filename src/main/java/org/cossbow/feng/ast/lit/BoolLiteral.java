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

    public BoolLiteral not() {
        return new BoolLiteral(pos(), !value);
    }

    @Override
    public String type() {
        return "bool";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BoolLiteral t)) return false;
        return value == t.value;
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
