package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.util.Optional;

/**
 * Used to initialize the {@code bool} type value.
 * <p>
 * But there are only two values: {@code true} or {@code false}.
 */
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
    public Optional<Primitive> compatible() {
        return Optional.of(Primitive.BOOL);
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
