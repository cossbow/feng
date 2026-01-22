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

    @Override
    public String type() {
        return "string";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StringLiteral f)) return false;
        return value.equals(f.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    //

    @Override
    public String toString() {
        return '"' + value + '"';
    }
}
