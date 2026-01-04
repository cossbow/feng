package org.cossbow.feng.analysis;


import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.UnaryOperator;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.parser.GlobalSymbolTable;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.visit.ExpressionParser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.function.BiFunction;

public class ConstExpressionParser implements ExpressionParser<Expression> {

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
            Map.entry(BinaryOperator.POW, ConstExpressionParser::pow));
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

    static BigInteger checkRange(BigInteger a) {
        if (a.bitLength() <= 64) return a;
        return ErrorUtil.unsupported("integer overflow");
    }

    static Literal calc(BinaryOperator op,
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
        return ErrorUtil.unsupported("integer not support " + op);
    }


    static final BigDecimal MaxFloat64 = new BigDecimal(Double.MAX_VALUE);
    static final BigDecimal MinFloat64 = new BigDecimal(Double.MIN_VALUE);

    static BigDecimal checkRange(BigDecimal a) {
        if (MinFloat64.compareTo(a) < 0 &&
                MaxFloat64.compareTo(a) > 0)
            return a;
        return ErrorUtil.unsupported("float overflow");
    }

    static BigDecimal pow(BigDecimal a, BigDecimal b) {
        double va = a.doubleValue(), vb = b.doubleValue();
        double vr = Math.pow(va, vb);
        if (Double.isInfinite(vr))
            ErrorUtil.unsupported("float overflow");
        return BigDecimal.valueOf(vr);
    }

    static final Map<BinaryOperator, BiFunction<BigDecimal, BigDecimal, BigDecimal>>
            floatMath = Map.ofEntries(
            Map.entry(BinaryOperator.ADD, BigDecimal::add),
            Map.entry(BinaryOperator.SUB, BigDecimal::subtract),
            Map.entry(BinaryOperator.MUL, BigDecimal::multiply),
            Map.entry(BinaryOperator.DIV, BigDecimal::divide),
            Map.entry(BinaryOperator.MOD, BigDecimal::remainder),
            Map.entry(BinaryOperator.POW, ConstExpressionParser::pow));

    static final Map<BinaryOperator, BiFunction<BigDecimal, BigDecimal, Boolean>>
            floatRel = Map.ofEntries(
            Map.entry(BinaryOperator.EQ, BigDecimal::equals),
            Map.entry(BinaryOperator.NE, (a, b) -> !a.equals(b)),
            Map.entry(BinaryOperator.GT, (a, b) -> a.compareTo(b) > 0),
            Map.entry(BinaryOperator.LT, (a, b) -> a.compareTo(b) < 0),
            Map.entry(BinaryOperator.GE, (a, b) -> a.compareTo(b) >= 0),
            Map.entry(BinaryOperator.LE, (a, b) -> a.compareTo(b) <= 0)
    );

    static Literal calc(BinaryOperator op,
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

        return ErrorUtil.unsupported("float not support " + op);
    }


    interface BoolFun {
        boolean calc(boolean a, boolean b);
    }

    static final Map<BinaryOperator, BoolFun>
            boolMath = Map.ofEntries(
            Map.entry(BinaryOperator.EQ, (a, b) -> a == b),
            Map.entry(BinaryOperator.NE, (a, b) -> a != b),
            Map.entry(BinaryOperator.AND, Boolean::logicalAnd),
            Map.entry(BinaryOperator.OR, Boolean::logicalOr),
            Map.entry(BinaryOperator.BITXOR, Boolean::logicalXor),
            Map.entry(BinaryOperator.BITAND, Boolean::logicalAnd),
            Map.entry(BinaryOperator.BITOR, Boolean::logicalOr));

    static Literal calc(BinaryOperator op,
                        BoolLiteral a,
                        BoolLiteral b) {
        var im = boolMath.get(op);
        if (im != null) {
            var c = im.calc(a.value(), b.value());
            return new BoolLiteral(a.pos(), c);
        }
        return ErrorUtil.unsupported("bool not support " + op);
    }


    static Literal calc(BinaryOperator op,
                        StringLiteral a,
                        StringLiteral b) {
        if (op != BinaryOperator.ADD)
            return ErrorUtil.unsupported("string not support " + op);

        return new StringLiteral(a.pos(), a.value() + b.value());
    }

    //

    private final GlobalSymbolTable table;

    public ConstExpressionParser(GlobalSymbolTable table) {
        this.table = table;
    }

    @Override
    public Expression visit(BinaryExpression e) {
        var op = e.operator();
        var le = visit(e.left());
        var re = visit(e.right());
        if ((le instanceof LiteralExpression lle &&
                re instanceof LiteralExpression rle)) {
            var ll = lle.literal();
            var rl = rle.literal();
            if (ll instanceof IntegerLiteral ill &&
                    rl instanceof IntegerLiteral irl)
                return new LiteralExpression(e.pos(),
                        calc(op, ill, irl));
            if (ll instanceof FloatLiteral fll &&
                    rl instanceof FloatLiteral frl)
                return new LiteralExpression(e.pos(),
                        calc(op, fll, frl));
            if (ll instanceof BoolLiteral bll &&
                    rl instanceof BoolLiteral brl)
                return new LiteralExpression(e.pos(),
                        calc(op, bll, brl));
            if (ll instanceof StringLiteral sll &&
                    rl instanceof StringLiteral srl)
                return new LiteralExpression(e.pos(),
                        calc(op, sll, srl));
        }

        return new BinaryExpression(e.pos(), op, le, re);
    }

    private List<Expression> visit(List<Expression> origin) {
        var result = new ArrayList<Expression>(origin.size());
        for (Expression el : origin) {
            result.add(visit(el));
        }
        return result;
    }

    @Override
    public Expression visit(ArrayExpression e) {
        return new ArrayExpression(e.pos(), visit(e.elements()));
    }

    @Override
    public Expression visit(AssertExpression e) {
        var subject = (PrimaryExpression) visit(e.subject());
        return new AssertExpression(e.pos(), subject, e.type());
    }

    @Override
    public Expression visit(CallExpression e) {
        var callee = (PrimaryExpression) visit(e.callee());
        return new CallExpression(e.pos(), callee, visit(e.arguments()));
    }

    @Override
    public Expression visit(IndexOfExpression e) {
        var subject = (PrimaryExpression) visit(e.subject());
        var index = visit(e.index());
        return new IndexOfExpression(e.pos(), subject, index);
    }

    @Override
    public Expression visit(LambdaExpression e) {
        return e;
    }

    @Override
    public Expression visit(LiteralExpression e) {
        return e;
    }

    @Override
    public Expression visit(MemberOfExpression e) {
        var subject = (PrimaryExpression) visit(e.subject());
        return new MemberOfExpression(e.pos(), subject,
                e.member(), e.generic());
    }

    @Override
    public Expression visit(NewExpression e) {
        var init = e.init().map(this::visit);
        return new NewExpression(e.pos(), e.type(), init);
    }

    @Override
    public Expression visit(ObjectExpression e) {
        var entries = new IdentifierTable<Expression>();
        for (int i = 0; i < e.entries().size(); i++) {
            var v = visit(e.entries().getValue(i));
            entries.add(e.entries().getKey(i), v);
        }
        return new ObjectExpression(e.pos(), entries);
    }

    @Override
    public Expression visit(PairsExpression e) {
        var pn = e.pairs().stream().map(pair -> {
            var k = visit(pair.key());
            var val = visit(pair.value());
            return new PairsExpression.Pair(k, val);
        }).toList();
        return new PairsExpression(e.pos(), pn);
    }

    @Override
    public Expression visit(ParenExpression e) {
        var child = visit(e.child());
        return new ParenExpression(e.pos(), child);
    }

    private final Set<Symbol> symbolSet = new HashSet<>();

    @Override
    public Expression visit(ReferExpression e) {
        var s = e.symbol();
        if (!symbolSet.add(s)) return ErrorUtil.semantic(
                "initialization cycle for %s", s);

        if (s.module().none()) {
            var v = table.variables.get(s.name());
            // todo: 引用常量，但是
        }
        return e;
    }

    @Override
    public Expression visit(UnaryExpression e) {
        var op = e.operator();
        var operand = visit(e.operand());
        if (operand instanceof LiteralExpression le) {
            var l = le.literal();
            if (l instanceof IntegerLiteral il) {
                var r = switch (op) {
                    case POSITIVE -> il.value();
                    case NEGATIVE -> il.value().negate();
                    case INVERT -> il.value().not();
                };
                return new LiteralExpression(e.pos(),
                        new IntegerLiteral(il.pos(),
                                r, il.radix()));
            } else if (l instanceof FloatLiteral fl) {
                BigDecimal r = switch (op) {
                    case POSITIVE -> fl.value();
                    case NEGATIVE -> fl.value().negate();
                    case INVERT -> ErrorUtil.unsupported(
                            "float not support: " + op);
                };
                return new LiteralExpression(e.pos(),
                        new FloatLiteral(fl.pos(), r));
            } else if (l instanceof BoolLiteral bl) {
                if (op == UnaryOperator.INVERT) {
                    return new LiteralExpression(e.pos(),
                            bl.not());
                }
            }

            return ErrorUtil.unsupported(
                    l.type() + " not support: " + op);
        }

        return new UnaryExpression(e.pos(), op, operand);
    }
}
