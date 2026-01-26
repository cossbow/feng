package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.lit.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.BiFunction;

import static org.cossbow.feng.util.ErrorUtil.semantic;

public class LiteralComputer {

    static final BigInteger MaxBits = BigInteger.valueOf(64);
    static final BigInteger MaxInt32 = BigInteger.valueOf(Integer.MAX_VALUE);

    static BigInteger pow(BigInteger base, BigInteger exp) {
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
            Map.entry(BinaryOperator.POW, LiteralComputer::pow));
    static final Map<BinaryOperator, BiFunction<BigInteger, BigInteger, BigInteger>>
            intgerBit = Map.ofEntries(
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
        var ib = intgerBit.get(op);
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
    static final BigDecimal MinFloat64 = new BigDecimal(Double.MIN_VALUE);

    public BigDecimal checkRange(BigDecimal a) {
        var v = a.abs();
        if (MinFloat64.compareTo(v) < 0 &&
                MaxFloat64.compareTo(v) > 0)
            return a;
        return semantic("float overflow");
    }

    static BigDecimal pow(BigDecimal a, BigDecimal b) {
        double va = a.doubleValue(), vb = b.doubleValue();
        double vr = Math.pow(va, vb);
        if (Double.isInfinite(vr))
            semantic("float overflow");
        return BigDecimal.valueOf(vr);
    }

    public static final Map<BinaryOperator, BiFunction<BigDecimal, BigDecimal, BigDecimal>>
            floatMath = Map.ofEntries(
            Map.entry(BinaryOperator.ADD, BigDecimal::add),
            Map.entry(BinaryOperator.SUB, BigDecimal::subtract),
            Map.entry(BinaryOperator.MUL, BigDecimal::multiply),
            Map.entry(BinaryOperator.DIV, BigDecimal::divide),
            Map.entry(BinaryOperator.MOD, BigDecimal::remainder),
            Map.entry(BinaryOperator.POW, LiteralComputer::pow));

    public static final Map<BinaryOperator, BiFunction<BigDecimal, BigDecimal, Boolean>>
            floatRel = Map.ofEntries(
            Map.entry(BinaryOperator.EQ, BigDecimal::equals),
            Map.entry(BinaryOperator.NE, (a, b) -> !a.equals(b)),
            Map.entry(BinaryOperator.GT, (a, b) -> a.compareTo(b) > 0),
            Map.entry(BinaryOperator.LT, (a, b) -> a.compareTo(b) < 0),
            Map.entry(BinaryOperator.GE, (a, b) -> a.compareTo(b) >= 0),
            Map.entry(BinaryOperator.LE, (a, b) -> a.compareTo(b) <= 0)
    );

    public Literal calc(BinaryOperator op,
                        FloatLiteral al,
                        FloatLiteral bl) {
        BigDecimal a = checkRange(al.value()), b = checkRange(bl.value());
        var im = floatMath.get(op);
        if (im != null) {
            var c = checkRange(im.apply(a, b));
            return new FloatLiteral(al.pos(), c);
        }
        var ir = floatRel.get(op);
        if (ir != null) {
            var r = ir.apply(a, b);
            return new BoolLiteral(al.pos(), r);
        }

        return semantic("float not support " + op);
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
