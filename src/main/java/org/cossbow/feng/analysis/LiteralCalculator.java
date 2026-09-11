package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.lit.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import static org.cossbow.feng.util.ErrorUtil.semantic;

/**
 * Constant binary expression calculator
 */
public class LiteralCalculator {

    static final BigInteger MaxBits = BigInteger.valueOf(64);
    static final BigInteger MaxInt32 = BigInteger.valueOf(Integer.MAX_VALUE);

    public static BigDecimal pow(BigDecimal base, BigInteger exp) {
        if (BigInteger.ZERO.equals(exp)) return BigDecimal.ONE;
        if (BigInteger.ONE.equals(exp)) return base;
        if (BigInteger.TWO.equals(exp)) return base.multiply(base);
        if (exp.compareTo(MaxInt32) < 0) return base.pow(exp.intValue());

        BigDecimal result = BigDecimal.ONE;
        BigInteger i = BigInteger.ZERO;
        while (i.compareTo(exp) < 0) {
            result = result.multiply(base);
            i = i.add(BigInteger.ONE);
        }
        return result;
    }

    public static BigInteger pow(BigInteger base, BigInteger exp) {
        if (BigInteger.ZERO.equals(exp)) return BigInteger.ONE;
        if (BigInteger.ONE.equals(exp)) return base;
        if (BigInteger.TWO.equals(exp)) return base.multiply(base);
        if (exp.compareTo(MaxInt32) < 0) return base.pow(exp.intValue());

        BigInteger result = BigInteger.ONE;
        BigInteger i = BigInteger.ZERO;
        while (i.compareTo(exp) < 0) {
            result = result.multiply(base);
            i = i.add(BigInteger.ONE);
        }
        return result;
    }

    static final Map<BinaryOperator, BiFunction<BigInteger, BigInteger, BigInteger>>
            integerMath = Map.ofEntries(
            Map.entry(BinaryOperator.ADD, BigInteger::add),
            Map.entry(BinaryOperator.SUB, BigInteger::subtract),
            Map.entry(BinaryOperator.MUL, BigInteger::multiply),
            Map.entry(BinaryOperator.DIV, BigInteger::divide),
            Map.entry(BinaryOperator.MOD, BigInteger::mod),
            Map.entry(BinaryOperator.POW, LiteralCalculator::pow));
    static final Map<BinaryOperator, BiFunction<BigInteger, BigInteger, BigInteger>>
            integerBit = Map.ofEntries(
            Map.entry(BinaryOperator.BITAND, BigInteger::and),
            Map.entry(BinaryOperator.BITXOR, BigInteger::xor),
            Map.entry(BinaryOperator.BITOR, BigInteger::or)
    );
    static final Map<BinaryOperator, BiFunction<BigInteger, BigInteger, Boolean>>
            integerRel = Map.ofEntries(
            Map.entry(BinaryOperator.EQ, BigInteger::equals),
            Map.entry(BinaryOperator.NE, (a, b) -> !a.equals(b)),
            Map.entry(BinaryOperator.GT, (a, b) -> a.compareTo(b) > 0),
            Map.entry(BinaryOperator.LT, (a, b) -> a.compareTo(b) < 0),
            Map.entry(BinaryOperator.GE, (a, b) -> a.compareTo(b) >= 0),
            Map.entry(BinaryOperator.LE, (a, b) -> a.compareTo(b) <= 0)
    );

    public BigInteger checkRange(BigInteger a) {
        if (a.bitLength() <= 64) return a;
        return semantic("integer overflow");
    }

    public Literal calc(BinaryOperator op,
                        IntegerLiteral al,
                        IntegerLiteral bl) {
        BigInteger a = checkRange(al.value()), b = checkRange(bl.value());

        var im = integerMath.get(op);
        if (im != null) {
            var c = checkRange(im.apply(a, b));
            return new IntegerLiteral(al.pos(), c, al.radix());
        }
        var ir = integerRel.get(op);
        if (ir != null) {
            var r = ir.apply(a, b);
            return new BoolLiteral(al.pos(), r);
        }
        var ib = integerBit.get(op);
        if (ib != null) {
            var c = checkRange(ib.apply(a, b));
            return new IntegerLiteral(al.pos(), c, al.radix());
        }
        if (op == BinaryOperator.LSHIFT) {
            var bs = b.mod(MaxBits);
            var c = a.shiftLeft(bs.intValue());
            return new IntegerLiteral(al.pos(), c, al.radix());
        }
        if (op == BinaryOperator.RSHIFT) {
            var bs = b.mod(MaxBits);
            var c = a.shiftRight(bs.intValue());
            return new IntegerLiteral(al.pos(), c, al.radix());
        }
        return semantic("integer not support " + op);
    }


    static final BigDecimal MaxFloat64 = new BigDecimal(Double.MAX_VALUE);

    /**
     * 有限值范围检查:超出 double 范围报 "float overflow"。
     * NaN/Infinity 不经过本方法。
     */
    public static BigDecimal checkRange(BigDecimal a) {
        if (MaxFloat64.compareTo(a.abs()) >= 0)
            return a;
        return semantic("float overflow");
    }

    /** double 值 → FloatLiteral:NaN/±Infinity 用特殊字面量,有限值过 BigDecimal 精确化。 */
    public static FloatLiteral wrapFloat(double v) {
        if (Double.isNaN(v)) return FloatLiteral.NaN;
        if (Double.isInfinite(v))
            return v > 0 ? FloatLiteral.POSITIVE_INFINITY
                    : FloatLiteral.NEGATIVE_INFINITY;
        return new FloatLiteral(Position.ZERO, checkRange(BigDecimal.valueOf(v)));
    }

    /** 把 FloatLiteral 包含的数值转成 double(特殊值原样转 IEEE-754)。 */
    public static double toDouble(FloatLiteral f) {
        if (f.value() != null) return f.value().doubleValue();
        return switch (f.special()) {
            case NAN -> Double.NaN;
            case POSITIVE_INFINITY -> Double.POSITIVE_INFINITY;
            case NEGATIVE_INFINITY -> Double.NEGATIVE_INFINITY;
        };
    }

    public static double pow(double a, double b) {
        return StrictMath.pow(a, b);
    }

    /** IEEE-754 关系运算:NaN 参与时 `==`/`<`/`>`/`<=`/`>=` 为 false,`!=` 为 true。 */
    public static boolean floatRel(BinaryOperator op, double a, double b) {
        return switch (op) {
            case EQ -> a == b;
            case NE -> a != b;
            case GT -> a > b;
            case LT -> a < b;
            case GE -> a >= b;
            case LE -> a <= b;
            default -> throw new IllegalArgumentException("not a relational op: " + op);
        };
    }

    /** 保留给其他调用方:true 在关系表内。 */
    public static boolean isRelOp(BinaryOperator op) {
        return floatRelKeys.contains(op);
    }

    static final Set<BinaryOperator> floatRelKeys =
            Set.of(BinaryOperator.EQ, BinaryOperator.NE, BinaryOperator.GT,
                    BinaryOperator.LT, BinaryOperator.GE, BinaryOperator.LE);

    /** double 域浮点折叠结果统一入口:NaN/Inf 特殊字面量,有限值检查范围。 */
    private static Literal floatResult(Position pos, double v) {
        if (Double.isNaN(v)) return FloatLiteral.NaN.cloneAt(pos);
        if (Double.isInfinite(v)) {
            return (v > 0 ? FloatLiteral.POSITIVE_INFINITY
                    : FloatLiteral.NEGATIVE_INFINITY).cloneAt(pos);
        }
        var c = checkRange(BigDecimal.valueOf(v));
        return new FloatLiteral(pos, c);
    }

    /**
     * Float 运算:先在 double 域用 IEEE-754 语义求值(NaN/Inf 是合法结果),
     * 有限结果再回 BigDecimal 域精确折叠;NaN 参与关系运算按 IEEE-754 规则。
     */
    public Literal calc(BinaryOperator op,
                        FloatLiteral al,
                        FloatLiteral bl) {
        double a = toDouble(al), b = toDouble(bl);
        if (isRelOp(op)) {
            return new BoolLiteral(al.pos(), floatRel(op, a, b));
        }
        double v = switch (op) {
            case ADD -> a + b;
            case SUB -> a - b;
            case MUL -> a * b;
            case DIV -> a / b;
            case MOD -> a % b;
            case POW -> StrictMath.pow(a, b);
            default -> throw new IllegalArgumentException("float not support " + op);
        };
        return floatResult(al.pos(), v);
    }

    // Float ^ Integer → Float
    public Literal calc(BinaryOperator op,
                        FloatLiteral al,
                        IntegerLiteral bl) {
        if (op != BinaryOperator.POW)
            return semantic("float-integer not support " + op);
        double a = toDouble(al);
        double v = StrictMath.pow(a, bl.value().doubleValue());
        return floatResult(al.pos(), v);
    }

    // Integer ^ Float → Float
    public Literal calc(BinaryOperator op,
                        IntegerLiteral al,
                        FloatLiteral bl) {
        if (op != BinaryOperator.POW)
            return semantic("integer-float not support " + op);
        double v = StrictMath.pow(al.value().doubleValue(), toDouble(bl));
        return floatResult(al.pos(), v);
    }

    public interface BoolFun {
        boolean calc(boolean a, boolean b);
    }

    public static final Map<BinaryOperator, BoolFun>
            boolMath = Map.ofEntries(
            Map.entry(BinaryOperator.EQ, (a, b) -> a == b),
            Map.entry(BinaryOperator.NE, (a, b) -> a != b),
            Map.entry(BinaryOperator.AND, Boolean::logicalAnd),
            Map.entry(BinaryOperator.OR, Boolean::logicalOr),
            Map.entry(BinaryOperator.BITXOR, Boolean::logicalXor),
            Map.entry(BinaryOperator.BITAND, Boolean::logicalAnd),
            Map.entry(BinaryOperator.BITOR, Boolean::logicalOr));

    public BoolLiteral calc(BinaryOperator op,
                            BoolLiteral a,
                            BoolLiteral b) {
        var im = boolMath.get(op);
        if (im != null) {
            var c = im.calc(a.value(), b.value());
            return new BoolLiteral(a.pos(), c);
        }
        return semantic("bool not support " + op);
    }


    public StringLiteral calc(BinaryOperator op,
                              StringLiteral a,
                              StringLiteral b) {
        if (op != BinaryOperator.ADD)
            return semantic("string not support " + op);

        return a.concat(b);
    }

    public BoolLiteral calc(BinaryOperator op,
                            NilLiteral a,
                            NilLiteral b) {
        return switch (op) {
            case EQ -> new BoolLiteral(a.pos(), true);
            case NE -> new BoolLiteral(a.pos(), false);
            case null, default -> semantic("nil not support " + op);
        };
    }


}
