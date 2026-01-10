package org.cossbow.feng.analysis;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.lit.BoolLiteral;
import org.cossbow.feng.ast.lit.FloatLiteral;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.mod.Global;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.stmt.ArrayTuple;
import org.cossbow.feng.ast.stmt.IfTuple;
import org.cossbow.feng.ast.stmt.ReturnTuple;
import org.cossbow.feng.ast.stmt.SwitchTuple;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.visit.SymbolContext;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class TypeDeducer implements EntityVisitor<TypeDeclarer> {

    private final SymbolContext context;

    public TypeDeducer(SymbolContext context) {
        this.context = context;
    }

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
    public TypeDeclarer visit(CurrentExpression e) {
        DefinedType dt;
        if (e.isSelf()) {
            dt = new DefinedType(e.pos(), e.type(), TypeArguments.EMPTY);
        } else {
            var type = (ClassDefinition) context.findType(e.type()).must();
            dt = type.parent().must();
        }
        return new DefinedTypeDeclarer(e.pos(), dt, Optional.empty());
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
        var td = visit(e.callee());
        if (td instanceof FuncTypeDeclarer ftd)
            return visit(ftd);
        if (td instanceof PrimitiveTypeDeclarer ptd)
            return visit(ptd);


        return ErrorUtil.semantic(
                "callee not callable: %s", e.pos());
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
                e.procedure().prototype(),
                TypeArguments.EMPTY);
    }

    @Override
    public TypeDeclarer visit(LiteralExpression e) {
        return switch (e.literal()) {
            case IntegerLiteral ee -> new PrimitiveTypeDeclarer(ee.pos(),
                    Primitive.INT);
            case FloatLiteral ee -> new PrimitiveTypeDeclarer(ee.pos(),
                    Primitive.FLOAT);
            case BoolLiteral ee -> new PrimitiveTypeDeclarer(ee.pos(),
                    Primitive.BOOL);
            case StringLiteral ee -> new MemTypeDeclarer(ee.pos(),
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

    private final Set<Symbol> symbolSet = new HashSet<>();

    @Override
    public TypeDeclarer visit(ReferExpression e) {
        var s = e.symbol();
        var fd = context.findFunc(s);
        if (fd.has())
            return new FuncTypeDeclarer(e.pos(),
                    fd.get().procedure().prototype(), e.generic());

        var v = context.findVar(s);
        if (v.has()) {
            if (!symbolSet.add(s)) return ErrorUtil.semantic(
                    "deduce type cycle for %s", s);
            return v.get().type().must();
        }

        if (s.module().none()) {
            var pri = Primitive.ofCode(s.name().value());
            if (pri.has())
                return new PrimitiveTypeDeclarer(e.pos(), pri.get());
        }

        return ErrorUtil.semantic("这是什么？%s", s);
    }

    //

    @Override
    public TypeDeclarer visit(FuncTypeDeclarer ftd) {
        var rs = ftd.prototype().returnSet();
        if (rs.isEmpty())
            return new VoidTypeDeclarer(ftd.pos());
        if (rs.size() == 1) {
            return rs.getFirst();
        }
        return new TupleTypeDeclarer(ftd.pos(), rs);
    }

    @Override
    public TypeDeclarer visit(PrimitiveTypeDeclarer e) {
        return e;
    }

    //

    @Override
    public TypeDeclarer visit(ArrayTuple e) {
        var list = new ArrayList<TypeDeclarer>(e.size());
        for (var v : e.values()) {
            var td = visit(v);
            list.add(td);
            if (!(td instanceof TupleTypeDeclarer))
                continue;
            ErrorUtil.semantic(
                    "tuple can't spread: %s",
                    v.toString());
        }
        return new TupleTypeDeclarer(e.pos(), list);
    }

    public TypeDeclarer visit(IfTuple e) {
        var y = visit(e.yes());
        var n = visit(e.not());
        return null;
    }

    public TypeDeclarer visit(SwitchTuple e) {
        return null;
    }

    @Override
    public TypeDeclarer visit(ReturnTuple e) {
        return null;
    }
}