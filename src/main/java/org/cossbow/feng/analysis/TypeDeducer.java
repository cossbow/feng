package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.MemType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.EnumDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
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
    private final TypeTool typeTool;

    public TypeDeducer(SymbolContext context) {
        this.context = context;
        typeTool = new TypeTool(context);
    }

    public boolean isNil(TypeDeclarer td) {
        return td instanceof LiteralTypeDeclarer dtd &&
                dtd.literal() instanceof NilLiteral;

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

    private Optional<TypeDeclarer> stringLiteralBinOp(
            TypeDeclarer l, TypeDeclarer r, BinaryExpression e) {
        if (l instanceof LiteralTypeDeclarer ltd &&
                r instanceof LiteralTypeDeclarer rtd) {
            var ll = ltd.literal();
            var rl = rtd.literal();
            if (ll instanceof StringLiteral ls
                    && rl instanceof StringLiteral rs) {
                if (e.operator() == BinaryOperator.ADD) {
                    return Optional.of(l);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<TypeDeclarer> primitiveBinOp(
            TypeDeclarer l, TypeDeclarer r, BinaryExpression e) {
        var lk = TypeTool.primitiveKind(l);
        var rk = TypeTool.primitiveKind(r);
        if (lk.none() || rk.none())
            return Optional.empty();

        Primitive.Kind lp = lk.must(), rp = rk.must();
        if (lp != rp)
            return semantic("require same type: %s", e.pos());

        var op = e.operator();
        switch (lp) {
            case INTEGER -> {
                if (BinaryOperator.SetMath.contains(op) ||
                        BinaryOperator.SetBits.contains(op))
                    return Optional.of(l);
                if (BinaryOperator.SetRel.contains(op))
                    return Optional.of(Primitive.BOOL.declarer(e.pos()));
            }
            case FLOAT -> {
                if (BinaryOperator.SetMath.contains(op))
                    return Optional.of(l);
                if (BinaryOperator.SetRel.contains(op))
                    return Optional.of(Primitive.BOOL.declarer(e.pos()));
            }
            case BOOL -> {
                if (BinaryOperator.SetLogic.contains(op))
                    return Optional.of(l);
            }
        }
        return Optional.empty();
    }

    private Optional<TypeDeclarer> derivedTypeBinOp(
            TypeDeclarer l, TypeDeclarer r, BinaryExpression e) {
        if (e.operator() != BinaryOperator.EQ &&
                e.operator() != BinaryOperator.NE)
            return Optional.empty();

        Optional<TypeDeclarer> ret = Optional.of(
                Primitive.BOOL.declarer(e.pos()));

        var lr = l.maybeRefer().has();
        var rr = r.maybeRefer().has();
        if (lr == rr) return l.equals(r) ? ret : Optional.empty();

        if (isNil(l) || isNil(r))
            return ret;

        return Optional.empty();
    }

    @Override
    public TypeDeclarer visit(BinaryExpression e) {
        var l = visit(e.left()).unmap();
        var r = visit(e.right()).unmap();

        var td = stringLiteralBinOp(l, r, e);
        if (td.has()) return td.get();

        td = primitiveBinOp(l, r, e);
        if (td.has()) return td.get();

        td = derivedTypeBinOp(l, r, e);
        if (td.has()) return td.get();

        return semantic("unsupported operate (%s %s %s): %s",
                l, e.operator(), r, e.pos());
    }

    @Override
    public TypeDeclarer visit(UnaryExpression e) {
        var t = visit(e.operand()).unmap();
        switch (e.operator()) {
            case INVERT -> {
                if (isInteger(t) || isBool(t)) return t;
            }
            case POSITIVE, NEGATIVE -> {
                if (isNumber(t)) return t;
            }
        }
        return semantic("unsupported operate (%s %s): %s",
                e.operator(), t, e.pos());
    }

    @Override
    public TypeDeclarer visit(CurrentExpression e) {
        DerivedType dt;
        if (e.isSelf()) {
            dt = new DerivedType(e.pos(), e.type(), TypeArguments.EMPTY);
        } else {
            var type = (ClassDefinition) context.findType(e.type()).must();
            dt = type.inherit().must();
        }
        return new DerivedTypeDeclarer(e.pos(), dt, Optional.empty());
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
    public TypeDeclarer visit(SizeofExpression e) {
        return Primitive.INT.declarer(e.pos());
    }

    public TypeDeclarer getCallable(PrimaryExpression e) {
        var td = visit(e);
        if (td instanceof FuncTypeDeclarer ftd)
            return ftd;

        if (td instanceof DerivedTypeDeclarer dtd) {
            var dt = context.findType(dtd.derivedType().symbol());
            if (dt.none())
                return semantic("type not defined: %s",
                        dtd.derivedType().pos());

            if (dt.must() instanceof PrototypeDefinition pd) {
                if (!pd.generic().isEmpty())
                    return unsupported("generic");
                return new FuncTypeDeclarer(e.pos(), pd.prototype(),
                        dtd.derivedType().generic());
            }
        }

        if (td instanceof ConvertorTypeDeclarer ctd)
            return ctd;

        return semantic("require a callable: %s", e.pos());
    }

    @Override
    public TypeDeclarer visit(CallExpression e) {
        var td = getCallable(e.callee());
        if (td instanceof FuncTypeDeclarer ftd)
            return visit(ftd);

        if (td instanceof ConvertorTypeDeclarer ctd)
            return ctd.primitive().declarer(ctd.pos());

        return td;
    }

    @Override
    public TypeDeclarer visit(IndexOfExpression e) {
        var td = visit(e.subject());
        if (td instanceof MemTypeDeclarer mtd) {
            if (mtd.mapped().none())
                return semantic("must map type before use: %s", e.pos());
            td = mtd.mapped().get();
        }
        if (td instanceof ArrayTypeDeclarer atd) {
            return atd.element();
        }
        if (td instanceof DefinitionDeclarer dd) {
            if (dd.definition() instanceof EnumDefinition ed) {
                return new DerivedTypeDeclarer(e.pos(),
                        new DerivedType(e.pos(), ed.symbol(),
                                TypeArguments.EMPTY), Optional.empty());
            }
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
        if (!(mtd instanceof DerivedTypeDeclarer dtd))
            return semantic("required user-defined-type: %s", mtd);

        var dt = dtd.derivedType();
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

        if (type instanceof InterfaceDefinition id) {
            var im = id.all().tryGet(member);
            if (im.has()) return Groups.g2(std, im.get());
            return semantic("%s has no field %s",
                    type, member);
        }

        if (type instanceof ClassDefinition cd) {
            var cf = cd.fields().tryGet(member);
            if (cf.has()) return Groups.g2(std, cf.get());
            var cm = cd.methods().tryGet(member);
            if (cm.has()) return Groups.g2(std, cm.get());
            return semantic("%s has no field %s",
                    type, member);
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
    public TypeDeclarer visit(MemberOfExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");

        var ev = typeTool.getEnum(e.subject(), e.member());
        if (ev.has()) return ev.get().a();

        var f = typeTool.getField(e.subject(), e.member());
        if (f.has()) {
            var g = f.get();
            var ft = g.b().type();
            if (g.a() instanceof MemTypeDeclarer mtd) {
                return new MemTypeDeclarer(e.pos(), new MemType(e.pos(),
                        mtd.readonly(), Optional.of(ft)), mtd.ref());
            }
            return ft;
        }

        var o = typeTool.getMethod(e.subject(), e.member());
        if (o.none()) return unreachable();
        return new FuncTypeDeclarer(e.member().pos(),
                o.get().prototype(), e.generic(), o);
    }

    @Override
    public TypeDeclarer visit(NewDerivedType t) {
        var ref = new Refer(t.pos(), STRONG, false, false);
        return new DerivedTypeDeclarer(t.pos(), t.type(), Optional.of(ref));
    }

    @Override
    public TypeDeclarer visit(NewArrayType dt) {
        return new ArrayTypeDeclarer(dt.pos(),
                dt.element(), Optional.empty(),
                Optional.of(new Refer(dt.pos(), STRONG,
                        true, dt.immutable())));
    }

    @Override
    public TypeDeclarer visit(NewMemType t) {
        return new MemTypeDeclarer(t.pos(),
                t.type(), new Refer(t.pos(), STRONG,
                true, false));
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
                return new ConvertorTypeDeclarer(s.pos(), p.get());
        }

        var v = context.findVar(s);
        if (v.has()) return v.get().type().must();

        var f = context.findFunc(s);
        if (f.has())
            return new FuncTypeDeclarer(s.pos(),
                    f.get().procedure().prototype(), e.generic());

        var t = context.findType(s);
        if (t.none())
            return semantic("undefined symbol '%s': %s", s, s.pos());

        return new DefinitionDeclarer(s.pos(), t.get());
    }

    //


    @Override
    public TypeDeclarer visit(Prototype e) {
        var rs = e.returnSet();
        if (rs.isEmpty())
            return new VoidTypeDeclarer(e.pos());
        if (rs.size() == 1) {
            return rs.getFirst();
        }
        return new TupleTypeDeclarer(e.pos(), rs);
    }

    @Override
    public TypeDeclarer visit(FuncTypeDeclarer ftd) {
        if (!ftd.generic().isEmpty())
            return unsupported("generic");
        return visit(ftd.prototype());
    }

    @Override
    public TypeDeclarer visit(ConvertorTypeDeclarer e) {
        return e.primitive().declarer(e.pos());
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

    @Override
    public TupleTypeDeclarer visit(ReturnTuple e) {
        var td = visit(e.call());

        if (td instanceof FuncTypeDeclarer ftd)
            return new TupleTypeDeclarer(e.pos(),
                    ftd.prototype().returnSet());

        if (td instanceof PrimitiveTypeDeclarer ptd)
            return new TupleTypeDeclarer(e.pos(),
                    List.of(ptd));

        if (td instanceof ConvertorTypeDeclarer ctd)
            return new TupleTypeDeclarer(e.pos(),
                    List.of(ctd.primitive().declarer(e.pos())));

        return new TupleTypeDeclarer(e.pos(),
                List.of(td));
    }
}