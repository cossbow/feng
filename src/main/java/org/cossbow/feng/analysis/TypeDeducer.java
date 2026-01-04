package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.lit.BoolLiteral;
import org.cossbow.feng.ast.lit.FloatLiteral;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.visit.ExpressionParser;

import java.math.BigInteger;

public class TypeDeducer implements ExpressionParser<TypeDeclarer> {

    boolean isPrimitive(TypeDeclarer td) {
        return td instanceof PrimitiveTypeDeclarer;
    }

    boolean isBool(TypeDeclarer td) {
        return td instanceof PrimitiveTypeDeclarer ptd
                && ptd.primitive() == Primitive.BOOL;
    }

    boolean isNumber(TypeDeclarer td) {
        return td instanceof PrimitiveTypeDeclarer ptd
                && ptd.primitive() != Primitive.BOOL;
    }

    boolean isInteger(TypeDeclarer td) {
        return td instanceof PrimitiveTypeDeclarer ptd
                && ptd.primitive() != Primitive.BOOL
                && ptd.primitive().integer;
    }

    @Override
    public TypeDeclarer visit(BinaryExpression e) {
        var lt = visit(e.left());
        var rt = visit(e.right());
        if (!lt.equals(rt)) return ErrorUtil.semantic(
                "type of tow operands must same: %s", e.pos());

        switch (e.operator()) {
            case POW, MUL, DIV, MOD, ADD, SUB, GT, LT, LE, GE -> {
                if (isNumber(lt) && isNumber(rt)) return lt;
            }
            case LSHIFT, RSHIFT, BITAND, BITXOR, BITOR -> {
                if (isInteger(lt) && isInteger(rt)) return lt;
            }
            case EQ, NE -> {
                if (isPrimitive(lt) && isPrimitive(rt)) return lt;
            }
            case AND, OR -> {
                if (isBool(lt) && isBool(rt)) return lt;
            }
        }
        return ErrorUtil.unsupported("binary-operation: %s %s %s",
                lt, e.operator(), rt);
    }

    @Override
    public TypeDeclarer visit(UnaryExpression e) {
        var t = visit(e.operand());
        switch (e.operator()) {
            case INVERT -> {
                if (isInteger(t) && isBool(t)) return t;
            }
            case POSITIVE, NEGATIVE -> {
                if (isNumber(t)) return t;
            }
        }
        return ErrorUtil.unsupported("unary-operation: %s %s %s",
                e.operator(), t);
    }

    @Override
    public TypeDeclarer visit(ArrayExpression e) {
        var td = visit(e.elements().getFirst());
        var le = new LiteralExpression(e.pos(),
                new IntegerLiteral(e.pos(),
                        BigInteger.valueOf(e.elements().size()),
                        10));
        return new ArrayTypeDeclarer(e.pos(),
                td, Optional.of(le), false);
    }

    @Override
    public TypeDeclarer visit(AssertExpression e) {
        return e.type();
    }

    @Override
    public TypeDeclarer visit(CallExpression e) {
        return ErrorUtil.unsupported("未完待续：推导返回值类型");
    }

    @Override
    public TypeDeclarer visit(IndexOfExpression e) {
        var td = visit(e.subject());
        if (td instanceof ArrayTypeDeclarer atd) {
            return atd.element();
        }
        return ErrorUtil.unsupported(
                "index required array");
    }

    @Override
    public TypeDeclarer visit(LambdaExpression e) {
        return new FuncTypeDeclarer(e.pos(),
                e.procedure().prototype());
    }

    @Override
    public TypeDeclarer visit(LiteralExpression e) {
        return switch (e.literal()) {
            case IntegerLiteral _ -> new PrimitiveTypeDeclarer(e.pos(),
                    Primitive.INT);
            case FloatLiteral _ -> new PrimitiveTypeDeclarer(e.pos(),
                    Primitive.FLOAT);
            case BoolLiteral _ -> new PrimitiveTypeDeclarer(e.pos(),
                    Primitive.BOOL);
            case StringLiteral _ -> new MemTypeDeclarer(e.pos(),
                    true, Optional.empty());
            case null, default -> ErrorUtil.semantic(
                    "can'nt infer the type by: %s", e.literal());
        };
    }

    @Override
    public TypeDeclarer visit(MemberOfExpression e) {
        return ErrorUtil.unsupported("未完待续：查询字段的类型");
    }

    @Override
    public TypeDeclarer visit(NewExpression e) {
        return switch (e.type()) {
            case NewDefinedType dt -> new DefinedTypeDeclarer(e.pos(),
                    dt.type(), Optional.of(new Reference(e.pos(),
                    ReferenceType.STRONG, false, false)));
            case NewArrayType dt -> new ArrayTypeDeclarer(e.pos(),
                    dt.element(), Optional.empty(), false);
            case null, default -> ErrorUtil.unreachable();
        };
    }

    @Override
    public TypeDeclarer visit(ObjectExpression e) {
        return null;
    }

    @Override
    public TypeDeclarer visit(PairsExpression e) {
        return null;
    }

    @Override
    public TypeDeclarer visit(ParenExpression e) {
        return visit(e.child());
    }

    @Override
    public TypeDeclarer visit(ReferExpression e) {
        return ErrorUtil.unsupported("未完待续：查询符号类型");
    }
}