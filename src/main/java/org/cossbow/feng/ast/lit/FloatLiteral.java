package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.util.Optional;

import java.math.BigDecimal;

public class FloatLiteral extends Literal {
    private final BigDecimal value;

    public FloatLiteral(Position pos, BigDecimal value) {
        super(pos);
        this.value = value;
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    public String type() {
        return "float";
    }

    @Override
    public Optional<Primitive> compatible() {
        return Optional.of(Primitive.FLOAT);
    }

    public IntegerLiteral toInteger() {
        return new IntegerLiteral(pos(), value.toBigInteger());
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FloatLiteral f)) return false;
        return value.equals(f.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    //
    @Override
    public String toString() {
        return value.toPlainString();
    }
}
