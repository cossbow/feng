package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.lit.BoolLiteral;
import org.cossbow.feng.ast.lit.FloatLiteral;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.stmt.ArrayTuple;
import org.cossbow.feng.ast.stmt.IfTuple;
import org.cossbow.feng.ast.stmt.ReturnTuple;
import org.cossbow.feng.ast.stmt.SwitchTuple;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.visit.SymbolContext;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.cossbow.feng.ast.dcl.ReferenceType.STRONG;
import static org.cossbow.feng.util.ErrorUtil.*;

public class TypeDeducer implements EntityVisitor<TypeDeclarer> {

    private final SymbolContext context;
    private final ConstExprCalc calc;

    public TypeDeducer(SymbolContext context) {
        this.context = context;
        calc = new ConstExprCalc(context);
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
        if (!lt.equals(rt)) return semantic(
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
        return unsupported("binary-operation: %s %s %s",
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
        return unsupported("unary-operation: %s %s %s",
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
                td, Optional.of(le), Optional.empty());
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
        return semantic(
                "callee not callable: %s", e.pos());
    }

    @Override
    public TypeDeclarer visit(IndexOfExpression e) {
        var td = visit(e.subject());
        if (td instanceof ArrayTypeDeclarer atd) {
            return atd.element();
        }
        return unsupported(
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
            case null, default -> semantic(
                    "can'nt infer the type by: %s", e.literal());
        };
    }

    public Entity getMember(PrimaryExpression subject,
                            Identifier member) {
        var td = visit(subject);
        if (td instanceof MemTypeDeclarer mtd) {
            if (mtd.mapped().none())
                return semantic("unmaped: %s", subject.pos());
            td = mtd.mapped().get();
        }
        if (!(td instanceof DefinedTypeDeclarer dtd))
            return semantic("required user-defined-type: %s", td);

        var dt = dtd.definedType();
        var o = context.findType(dt.symbol());
        if (o.none()) return semantic(
                "undefined type: %s", dt.symbol());
        var type = o.get();
        if (type instanceof StructureDefinition sd) {
            var sf = sd.fields().tryGet(member);
            if (sf.has()) return sf.get();

            return semantic("%s %s has no member %s",
                    sd.union() ? "union" : "struct",
                    type.name(), member);
        }
        if (type instanceof ClassDefinition cd) {
            var cf = cd.fields().tryGet(member);
            if (cf.has()) return cf.get();
            var cm = cd.methods().tryGet(member);
            if (cm.has()) return cm.get();
            return semantic("class %s has no field %s",
                    type.name(), member);
        }

        return semantic("%s can't have member", type.name());
    }

    @Override
    public TypeDeclarer visit(MemberOfExpression mo) {
        if (!mo.generic().isEmpty()) return unsupported("generic");

        var m = getMember(mo.subject(), mo.member());

        if (m instanceof StructureField sf) return sf.type();

        if (m instanceof ClassField cf) return cf.type();

        if (m instanceof ClassMethod cm)
            return new FuncTypeDeclarer(cm.pos(),
                    cm.procedure().prototype(), mo.generic());

        return unreachable();
    }

    @Override
    public TypeDeclarer visit(NewExpression e) {
        return switch (e.type()) {
            case NewDefinedType dt -> new DefinedTypeDeclarer(e.pos(),
                    dt.type(), Optional.of(new Reference(e.pos(),
                    STRONG, false, false)));
            case NewArrayType dt -> new ArrayTypeDeclarer(e.pos(),
                    dt.element(), Optional.empty(), Optional.of(
                    new Reference(dt.pos(), STRONG, true, dt.immutable())));
            case null, default -> unreachable();
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
            if (!symbolSet.add(s)) return semantic(
                    "deduce type cycle for %s", s);
            var td = v.get().type().must();
            visit(td);
            return v.get().type().must();
        }

        if (s.module().none()) {
            var pri = Primitive.ofCode(s.name().value());
            if (pri.has())
                return new PrimitiveTypeDeclarer(e.pos(), pri.get());
        }

        return semantic("这是什么？%s", s);
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
    public TupleTypeDeclarer visit(ArrayTuple e) {
        var list = new ArrayList<TypeDeclarer>(e.size());
        for (var v : e.values()) {
            var td = visit(v);
            list.add(td);
            if (!(td instanceof TupleTypeDeclarer))
                continue;
            return semantic("nested tuple: %s", v.pos());
        }
        return new TupleTypeDeclarer(e.pos(), list);
    }

    public TypeDeclarer visit(IfTuple e) {
        return unsupported("if-tuple");
    }

    public TypeDeclarer visit(SwitchTuple e) {
        return unsupported("switch-tuple");
    }

    @Override
    public TupleTypeDeclarer visit(ReturnTuple e) {
        var td = visit(e.call());
        if (td instanceof FuncTypeDeclarer ftd)
            return new TupleTypeDeclarer(e.pos(),
                    ftd.prototype().returnSet());
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            return new TupleTypeDeclarer(e.pos(),
                    List.of(ptd));
        }
        return unreachable();
    }
}