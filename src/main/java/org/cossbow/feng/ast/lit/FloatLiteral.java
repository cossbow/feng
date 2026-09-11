package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.util.Optional;

import java.math.BigDecimal;
import java.math.BigInteger;
/**
 * Used to initialize the {@link Primitive.Kind#FLOAT} value.
 *
 * <p>有限值用 {@link BigDecimal} 精确表示;IEEE-754 特殊值(NaN、±Infinity)
 * 用 {@link #NAN} / {@link #INFINITY} 静态实例表示(编译期常量折叠产物)。
 */
public class FloatLiteral extends Literal {
    public enum Special {
        NAN, POSITIVE_INFINITY, NEGATIVE_INFINITY
    }

    private final BigDecimal value;
    private final Special special;

    private static final Position ZERO_POS = Position.ZERO;

    public static final FloatLiteral NaN =
            new FloatLiteral(ZERO_POS, null, Special.NAN);
    public static final FloatLiteral POSITIVE_INFINITY =
            new FloatLiteral(ZERO_POS, null, Special.POSITIVE_INFINITY);
    public static final FloatLiteral NEGATIVE_INFINITY =
            new FloatLiteral(ZERO_POS, null, Special.NEGATIVE_INFINITY);

    public FloatLiteral(Position pos, BigDecimal value) {
        super(pos);
        this.value = value;
        this.special = null;
    }

    private FloatLiteral(Position pos, BigDecimal value, Special special) {
        super(pos);
        this.value = value;
        this.special = special;
    }

    public BigDecimal value() {
        return value;
    }

    /** 是否为 IEEE-754 特殊值(NaN / ±Infinity)。 */
    public boolean isSpecial() {
        return special != null;
    }

    public Special special() {
        return special;
    }

    /** 保留位置信息:特殊值单例在折叠时借用操作数位置。 */
    public FloatLiteral cloneAt(Position pos) {
        if (special == null) return new FloatLiteral(pos, value);
        return new FloatLiteral(pos, null, special);
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
        if (isSpecial()) {
            // NaN/Inf 不能转整数,调用方应已检查;退回 0
            return new IntegerLiteral(pos(), BigInteger.ZERO);
        }
        return new IntegerLiteral(pos(), value.toBigInteger());
    }

    //
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FloatLiteral f)) return false;
        if (isSpecial() || f.isSpecial())
            return special == f.special;
        return value.equals(f.value);
    }

    @Override
    public int hashCode() {
        if (isSpecial()) return special.hashCode();
        return value.hashCode();
    }

    //
    @Override
    public String toString() {
        if (special != null) {
            return switch (special) {
                case NAN -> "NAN";
                case POSITIVE_INFINITY -> "INFINITY";
                case NEGATIVE_INFINITY -> "-INFINITY";
            };
        }
        return value.toPlainString();
    }
}
