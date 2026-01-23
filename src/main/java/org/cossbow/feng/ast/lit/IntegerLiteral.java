package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.util.Optional;

import java.math.BigInteger;

public class IntegerLiteral extends Literal implements Comparable<IntegerLiteral> {
    private final BigInteger value;
    private final transient int radix;

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

    public int compareTo(BigInteger v) {
        return value.compareTo(v);
    }

    @Override
    public int compareTo(IntegerLiteral o) {
        return value.compareTo(o.value);
    }

    public boolean isNegative() {
        return value.compareTo(BigInteger.ZERO) < 0;
    }

    //

    @Override
    public Optional<Primitive.Kind> compatible() {
        return Optional.of(Primitive.Kind.INTEGER);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IntegerLiteral f)) return false;
        return value.equals(f.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String type() {
        return "integer";
    }

    //

    @Override
    public String toString() {
        return value.toString(radix);
    }
}
