package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;

import java.math.BigInteger;

public class IntegerLiteral extends Literal {
    private final BigInteger value;
    private final int radix;

    public IntegerLiteral(Position pos,
                          BigInteger value,
                          int radix) {
        super(pos);
        this.value = value;
        this.radix = radix;
    }

    public BigInteger value() {
        return value;
    }

    public int radix() {
        return radix;
    }

    @Override
    public String type() {
        return "integer";
    }

    @Override
    public String toString() {
        return value.toString(radix);
    }
}
