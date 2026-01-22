package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.util.Optional;

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
    public Optional<Primitive.Kind> compatible() {
        return Optional.of(Primitive.Kind.BOOL);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof BoolLiteral t
                && value == t.value;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(value);
    }
    //

    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
