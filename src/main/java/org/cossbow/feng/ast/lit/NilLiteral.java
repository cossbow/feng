package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;

/**
 * The literal {@code nil} is used to initliaze
 * reference or functional type.
 */
public class NilLiteral extends Literal {
    public NilLiteral(Position pos) {
        super(pos);
    }

    @Override
    public String type() {
        return "nil";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IntegerLiteral;
    }

    @Override
    public int hashCode() {
        return type().hashCode();
    }

    //

    @Override
    public String toString() {
        return type();
    }
}
