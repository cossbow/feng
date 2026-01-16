package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierTable;
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
import org.cossbow.feng.ast.oop.EnumDefinition;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.visit.SymbolContext;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.cossbow.feng.ast.dcl.ReferKind.STRONG;
import static org.cossbow.feng.util.ErrorUtil.*;

public class TypeDeducer implements EntityVisitor<TypeDeclarer> {

    private final SymbolContext context;

    public TypeDeducer(SymbolContext context) {
        this.context = context;
    }

    public boolean isPrimitive(TypeDeclarer td) {
        return td instanceof PrimitiveTypeDeclarer;
    }

    public boolean isBool(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd)
            return ptd.primitive().isBool();

        if (td instanceof LiteralTypeDeclarer ltd)
            return ltd.literal() instanceof BoolLiteral;

        return false;
    }

    public boolean isNumber(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd)
            return !ptd.primitive().isBool();

        if (td instanceof LiteralTypeDeclarer ltd)
            return ltd.literal() instanceof IntegerLiteral ||
                    ltd.literal() instanceof FloatLiteral;

        return false;
    }

    public boolean isInteger(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd)
            return ptd.primitive().isInteger();

        if (td instanceof LiteralTypeDeclarer ltd)
            return ltd.literal() instanceof IntegerLiteral;

        return false;
    }

    @Override
    public TypeDeclarer visit(BinaryExpression e) {
        var lt = visit(e.left());
        var rt = visit(e.right());
        if (!lt.equals(rt)) return semantic(
                "type of tow operands must same: %s", e.pos());
        if (!(lt instanceof PrimitiveTypeDeclarer lpt &&
                rt instanceof PrimitiveTypeDeclarer rpt))
            return unsupported("binary-operation: %s", e.pos());

        Primitive lp = lpt.primitive(), rp = rpt.primitive();
        switch (e.operator()) {
            case POW, MUL, DIV, MOD, ADD, SUB -> {
                if (!lp.isBool() && !rp.isBool()) return lt;
            }
            case LSHIFT, RSHIFT, BITAND, BITXOR, BITOR -> {
                if (lp.isInteger() && rp.isInteger()) return lt;
                if (lp.isBool() && rp.isBool()) return lt;
            }
            case GT, LT, LE, GE -> {
                if (!lp.isBool() && !rp.isBool())
                    return Primitive.BOOL.declarer(lt.pos());
            }
            case EQ, NE -> {
                return Primitive.BOOL.declarer(lt.pos());
            }
            case AND, OR -> {
                if (lp.isBool() && rp.isBool()) return lt;
            }
        }
        return semantic("binary-operation: %s %s %s",
                lt, e.operator(), rt);
    }

    @Override
    public TypeDeclarer visit(UnaryExpression e) {
        var t = visit(e.operand());
        switch (e.operator()) {
            case INVERT -> {
                if (isInteger(t) || isBool(t)) return t;
            }
            case POSITIVE, NEGATIVE -> {
                if (isNumber(t)) return t;
            }
        }
        return unsupported("unary-operation: %s %s",
                e.operator(), t);
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
    public TypeDeclarer visit(ArrayExpression e) {
        if (e.elements().isEmpty())
            return new VoidTypeDeclarer(e.pos());

        var td = visit(e.elements().getFirst());
        var le = new LiteralExpression(e.pos(),
                new IntegerLiteral(e.pos(),
                        BigInteger.valueOf(e.elements().size()),
                        10));
        return new ArrayTypeDeclarer(e.pos(), td,
                Optional.of(le), Optional.empty(), true);
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

        return td;
    }

    @Override
    public TypeDeclarer visit(IndexOfExpression e) {
        var td = visit(e.subject());
        if (td instanceof ArrayTypeDeclarer atd) {
            return atd.element();
        }
        return unsupported("index required array");
    }

    @Override
    public TypeDeclarer visit(LambdaExpression e) {
        return new FuncTypeDeclarer(e.pos(),
                e.procedure().prototype(),
                TypeArguments.EMPTY);
    }

    @Override
    public TypeDeclarer visit(LiteralExpression e) {
        return new LiteralTypeDeclarer(e.pos(), e.literal());
    }

    public Groups.G2<TypeDeclarer, Entity>
    getMember(PrimaryExpression subject,
              Identifier member) {
        var std = visit(subject);
        var mtd = std;
        if (mtd instanceof MemTypeDeclarer mem) {
            if (mem.mapped().none())
                return semantic("unmaped: %s", subject.pos());
            mtd = mem.mapped().get();
        }
        if (!(mtd instanceof DefinedTypeDeclarer dtd))
            return semantic("required user-defined-type: %s", mtd);

        var dt = dtd.definedType();
        var o = context.findType(dt.symbol());
        if (o.none()) return semantic(
                "undefined type: %s", dt.symbol());
        var type = o.get();

        if (type instanceof StructureDefinition sd) {
            var sf = sd.fields().tryGet(member);
            if (sf.has()) return Groups.g2(std, sf.get());

            return semantic("%s %s has no member %s",
                    sd.domain(), type.symbol(), member);
        }

        if (type instanceof ClassDefinition cd) {
            var cf = cd.fields().tryGet(member);
            if (cf.has()) return Groups.g2(std, cf.get());
            var cm = cd.methods().tryGet(member);
            if (cm.has()) return Groups.g2(std, cm.get());
            return semantic("class %s has no field %s",
                    type.symbol(), member);
        }

        if (type instanceof EnumDefinition ed) {
            var ev = ed.values().tryGet(member);
            if (ev.has()) return Groups.g2(std, ev.get());
            return semantic("enum %s.%s not define: %s",
                    type.symbol(), member, member.pos());
        }

        return semantic("%s can't have member", type.symbol());
    }

    @Override
    public TypeDeclarer visit(MemberOfExpression mo) {
        if (!mo.generic().isEmpty()) return unsupported("generic");

        var g2 = getMember(mo.subject(), mo.member());

        if (g2.b() instanceof StructureField sf) return sf.type();

        if (g2.b() instanceof ClassField cf) return cf.type();

        if (g2.b() instanceof ClassMethod cm)
            return new FuncTypeDeclarer(cm.pos(),
                    cm.func().prototype(), mo.generic());

        if (g2.b() instanceof EnumDefinition.Value)
            return g2.a();

        return unreachable();
    }

    @Override
    public TypeDeclarer visit(NewDefinedType dt) {
        return new DefinedTypeDeclarer(dt.pos(),
                dt.type(), Optional.of(new Refer(dt.pos(),
                STRONG, false, false)));
    }

    @Override
    public TypeDeclarer visit(NewArrayType dt) {
        return new ArrayTypeDeclarer(dt.pos(),
                dt.element(), Optional.empty(),
                Optional.of(new Refer(dt.pos(), STRONG,
                        true, dt.immutable())));
    }

    @Override
    public TypeDeclarer visit(NewMemType e) {
        return new MemTypeDeclarer(e.pos(),
                false,
                Optional.of(new Refer(e.pos(), STRONG,
                        true, false)),
                e.mapped());
    }

    @Override
    public TypeDeclarer visit(NewExpression e) {
        return visit(e.type());
    }

    @Override
    public TypeDeclarer visit(ObjectExpression oe) {
        var entries = oe.entries().map(this::visit);
        return new ObjectTypeDeclarer(oe.pos(), new IdentifierTable<>(entries));
    }

    @Override
    public TypeDeclarer visit(PairsExpression e) {
        return unsupported("pairs");
    }

    @Override
    public TypeDeclarer visit(ParenExpression e) {
        return visit(e.child());
    }

    @Override
    public TypeDeclarer visit(ReferExpression e) {
        var s = e.symbol();
        if (s.module().none()) {
            var p = Primitive.ofCode(s.name().value());
            if (p.has())
                return p.get().declarer(e.pos());
        }
        var f = context.findFunc(s);
        if (f.has())
            return new FuncTypeDeclarer(e.pos(),
                    f.get().procedure().prototype(), e.generic());

        var v = context.findVar(s);
        if (v.has()) return v.get().type().must();

        var t = context.findType(s);
        if (t.none())
            return semantic("unknown symbol '%s' ?", s);

        if (t.get() instanceof PrimitiveDefinition pd)
            return pd.primitive().declarer(e.pos());

        return new DefinedTypeDeclarer(e.pos(),
                new DefinedType(e.pos(), t.get().symbol(),
                        TypeArguments.EMPTY), Optional.empty());
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

    @Override
    public TypeDeclarer visit(LiteralTypeDeclarer e) {
        return e;
    }

    //


    @Override
    public TupleTypeDeclarer visit(Tuple e) {
        var td = EntityVisitor.super.visit(e);
        if (td instanceof TupleTypeDeclarer ttd)
            return ttd;

        return unreachable();
    }

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