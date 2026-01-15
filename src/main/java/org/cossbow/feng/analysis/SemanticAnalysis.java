package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.ast.var.IndexAssignableOperand;
import org.cossbow.feng.ast.var.FieldAssignableOperand;
import org.cossbow.feng.ast.var.VariableAssignableOperand;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.util.Stack;
import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.visit.SymbolContext;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.cossbow.feng.util.ErrorUtil.*;

public class SemanticAnalysis implements EntityVisitor<Entity> {

    private final StackedContext context;
    private final TypeDeducer typeDeducer;
    private final ConstChecker constChecker;

    public SemanticAnalysis(SymbolContext parent) {
        context = new StackedContext(parent);
        typeDeducer = new TypeDeducer(context);
        constChecker = new ConstChecker(context);
    }

    //

    private Expression compute(Expression e) {
        return new ConstExprComputer(context).visit(e);
    }

    private LiteralExpression computeConst(Expression e) {
        var res = compute(e);
        if (res instanceof LiteralExpression le)
            return le;

        return semantic("require const value: %s", e.pos());
    }

    @Override
    public Entity visit(Source s) {
        var tab = s.table();
        for (var f : tab.variables.values()) {
            visit(f);
        }
        for (var t : tab.namedTypes.values()) visit(t);
        for (var t : tab.unnamedTypes.values()) visit(t);
        for (var f : tab.namedFunctions.values()) visit(f);

        return s;
    }

    @Override
    public Entity visit(GlobalVariable gv) {
        if (gv.init().none()) {
            if (gv.declare() == Declare.CONST) {
                return semantic("const must init: %s", gv.pos());
            }
            // TODO: 值默认值
            return gv;
        }

        var et = typeDeducer.visit(gv.init().get());
        gv.type().set(et);

        var e = compute(gv.init().get());
        if (!e.isFinal()) {
            return semantic("require final value: %s%s",
                    gv.name(), gv.pos());
        }
        gv.init().set(e);

        visit((Variable) gv);
        visit(gv.init().must());
        return gv;
    }

    @Override
    public Entity visit(Modifier m) {
        if (!m.attributes().isEmpty())
            unsupported("attribute");
        return m;
    }

    @Override
    public Entity visit(Refer e) {
        if (e.kind() == ReferKind.WEAK)
            unsupported("weak-reference");
        if (e.required())
            unsupported("require reference");
        return e;
    }

    @Override
    public Entity visit(TypeArguments ta) {
        if (ta.isEmpty()) return ta;
        return unsupported("generic");
    }

    @Override
    public TypeDefinition visit(DefinedType dt) {
        var type = context.findType(dt.symbol());
        if (type.none())
            return semantic("%s not defined", dt.symbol());
        if (!type.get().generic().isEmpty())
            return unsupported("generic");
        if (!dt.generic().isEmpty())
            return unsupported("generic");
        return type.get();
    }

    @Override
    public Entity visit(DefinedTypeDeclarer td) {
        var dt = visit(td.definedType());
        if (td.refer().none()) return td;
        var r = td.refer().get();
        visit(r);
        if (dt instanceof ClassDefinition ||
                dt instanceof InterfaceDefinition)
            return td;

        return semantic("can't refer %s", dt.domain());
    }

    @Override
    public Entity visit(ArrayTypeDeclarer td) {
        visit(td.element());
        td.length().use(this::visit);
        td.refer().use(this::visit);
        return td;
    }

    @Override
    public Entity visit(FuncTypeDeclarer td) {
        visit(td.prototype());
        visit(td.generic());
        return td;
    }

    private boolean checkMappable(DefinedTypeDeclarer dtd) {
        if (dtd.refer().has())
            return semantic("mem can't map reference");
        var type = visit(dtd.definedType());
        return type instanceof StructureDefinition;
    }

    private boolean checkMappable(ArrayTypeDeclarer td) {
        if (td.refer().has()) return false;
        return checkMappable(td.element());
    }

    private boolean checkMappable(TypeDeclarer td) {
        return switch (td) {
            case PrimitiveTypeDeclarer ignored -> true;
            case DefinedTypeDeclarer dtd -> checkMappable(dtd);
            case ArrayTypeDeclarer atd -> checkMappable(atd);
            case null -> unreachable();
            default -> false;
        };
    }

    @Override
    public Entity visit(MemTypeDeclarer mtd) {
        if (mtd.mapped().none()) return mtd;
        var map = mtd.mapped().get();
        visit(map);
        if (checkMappable(map)) return mtd;
        return semantic("can't map type %s", map.pos());
    }

    @Override
    public Entity visit(PrimitiveTypeDeclarer ptd) {
        return ptd;
    }

    @Override
    public Entity visit(ThisTypeDeclarer ctd) {
        if (enterClass == null)
            semantic("this only use in class");
        return ctd;
    }

    @Override
    public Entity visit(TupleTypeDeclarer ttd) {
        ttd.tuple().forEach(this::visit);
        return ttd;
    }

    @Override
    public Entity visit(TypeParameters e) {
        if (e.isEmpty()) return e;
        return unsupported("generic");
    }

    @Override
    public Entity visit(TypeDefinition def) {
        visit(def.modifier());
        visit(def.generic());
        EntityVisitor.super.visit(def);
        return def;
    }

    @Override
    public Entity visit(PrimitiveDefinition e) {
        return e;
    }

    // structure define

    @Override
    public Entity visit(StructureDefinition def) {
        if (!def.generic().isEmpty())
            return unsupported("generic");

        for (var f : def.fields().values())
            visit(f);

        return def;
    }

    private void structFieldType(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            if (ptd.primitive().isBool()) {
                semantic("require integer or float: %s", ptd.pos());
            }
            return;
        }

        if (td instanceof DefinedTypeDeclarer dtd) {
            if (dtd.refer().has()) {
                semantic("can't be reference");
                return;
            }
            if (!dtd.definedType().generic().isEmpty()) {
                unsupported("generic");
                return;
            }
            var def = context.findType(dtd.definedType().symbol());
            if (def.none()) {
                semantic("not define: %s", dtd.definedType().pos());
                return;
            }
            if (def.get() instanceof StructureDefinition)
                return;

            semantic("require structure type: %s", dtd.definedType().pos());
            return;
        }

        if (td instanceof ArrayTypeDeclarer atd) {
            if (atd.refer().has()) {
                semantic("can't be reference");
                return;
            }
            var len = computeConst(atd.length().must());
            var i = len.asInteger();
            if (i.none()) {
                semantic("require integer");
                return;
            }
            if (i.get().value().compareTo(BigInteger.ZERO) < 0) {
                semantic("can't be negative: %s", atd.length().get().pos());
            }
            structFieldType(atd.element());
            return;
        }

        semantic("illegal type: %s%s", td, td.pos());
    }

    private void structBitfield(Expression bf) {
        var bv = compute(bf);
        if (!(bv instanceof LiteralExpression le)) {
            semantic("require const value: %s", bf.pos());
            return;
        }

        var il = le.asInteger();
        if (!il.has()) {
            semantic("require integer: %s", bf.pos());
            return;
        }

        var v = il.get().value().intValue();
        if (0 < v && v <= 64)
            return;

        semantic("bitfield must >0 and <=64 ");
    }

    @Override
    public Entity visit(StructureField sf) {
        visit(sf.type());
        structFieldType(sf.type());

        if (sf.bitfield().has())
            structBitfield(sf.bitfield().get());

        return sf;
    }

    // enum

    @Override
    public Entity visit(EnumDefinition def) {
        for (var v : def.values().values())
            visit(v.init());

        return def;
    }


    // class define

    private volatile ClassDefinition enterClass;
    private volatile ClassMethod enterMethod;

    @Override
    public Entity visit(ClassDefinition cd) {
        assert enterClass == null;
        enterClass = cd;
        context.enterScope(cd);
        if (!cd.generic().isEmpty())
            return unsupported("generic");

        cd.parent().use(p -> {
            var t = visit(p);
            if (!(t instanceof ClassDefinition))
                semantic("require class: %s", p.pos());
        });
        for (var i : cd.impl().values()) {
            var t = visit(i);
            if (!(t instanceof InterfaceDefinition))
                semantic("require interface: %s", i.pos());
        }
        for (var f : cd.fields().values()) visit(f);
        for (var m : cd.methods().values()) visit(m);

        if (!cd.macros().isEmpty())
            unsupported("macro");
        context.exitScope();
        enterClass = null;
        return cd;
    }

    @Override
    public Entity visit(ClassField cf) {
        assert enterClass != null;
        if (cf.type() instanceof DefinedTypeDeclarer ctd) {
            var isPhantom = ctd.refer().match(
                    r -> r.checkType(ReferKind.PHANTOM));
            if (!isPhantom) return visit(ctd);
            return semantic("can't be phantom-reference: %s",
                    ctd.pos());
        }
        return cf;
    }

    @Override
    public Entity visit(ClassMethod cm) {
        assert enterClass != null;
        assert enterMethod == null;
        enterMethod = cm;
        if (!cm.func().generic().isEmpty())
            unsupported("generic");
        visit(cm.func().modifier());
        visit(cm.func().procedure());
        enterMethod = null;
        return cm;
    }

    @Override
    public Entity visit(InterfaceDefinition def) {
        for (DefinedType p : def.parts().values()) {
            var t = visit(p);
            if (t instanceof InterfaceDefinition)
                continue;
            return semantic("require interface: %s");
        }

        for (var m : def.methods().values())
            visit(m);

        return def;
    }

    @Override
    public Entity visit(InterfaceMethod m) {
        visit(m.generic());
        visit(m.prototype());
        return m;
    }

    @Override
    public Entity visit(PrototypeDefinition pd) {
        visit(pd.generic());
        visit(pd.prototype(), false);
        return pd;
    }

    //

    @Override
    public Entity visit(FunctionDefinition fd) {
        visit(fd.generic());
        visit(fd.modifier());
        visit(fd.procedure());
        return fd;
    }

    private volatile Procedure enterProc;

    @Override
    public Entity visit(Procedure proc) {
        assert enterProc == null;
        enterProc = proc;
        context.enterScope();
        visit(proc.prototype(), true);
        visit(proc.body());
        context.exitScope();
        enterProc = null;
        return proc;
    }


    public Entity visit(Prototype prot, boolean addVar) {
        visit(prot.parameterSet(), addVar);
        prot.returnSet().forEach(this::visit);
        return prot;
    }

    private void visit(ParameterSet ps, boolean addVar) {
        switch (ps) {
            case UnnamedParameterSet ups -> visit(ups);
            case VariableParameterSet vps -> visit(vps, addVar);
            case null -> unreachable();
            default -> {
            }
        }
    }

    private void visit(UnnamedParameterSet ps) {
        for (var td : ps.types()) {
            if (td instanceof DefinedTypeDeclarer dtd) {
                var isWeak = dtd.refer().match(
                        r -> r.checkType(ReferKind.WEAK));
                if (isWeak)
                    semantic("can't be weak-reference: %s",
                            dtd.pos());
                dtd.refer().use(this::visit);
            }
            visit(td);
        }
    }

    private void visit(VariableParameterSet ps, boolean addVar) {
        for (Variable v : ps.variables().values()) {
            visit(v);
            if (addVar) context.putVar(v);
        }
    }

    //

    @Override
    public Entity visit(BlockStatement bs) {
        context.enterScope();
        for (var s : bs.list())
            visit(s);
        context.exitScope();
        return bs;
    }

    @Override
    public Entity visit(DeclarationStatement ds) {
        if (ds.init().has()) {
            visit(ds.init().get());
            var td = typeDeducer.visit(ds.init().get());
            var types = td.tuple();
            if (types.size() != ds.variables().size())
                return semantic("unaligned declaration: %s", ds.pos());
            var i = 0;
            for (var v : ds.variables()) {
                var t = types.get(i++);
                if (v.type().none()) {
                    v.type().set(t);
                } else if (!assignable(v.type().get(), t)) {
                    semantic("unassignable: %s", v.pos());
                }
            }
        }
        for (var v : ds.variables()) {
            assert v.type().has();
            visit(v);
            context.putVar(v);
        }
        return ds;
    }

    private boolean
    assignable(ArrayTypeDeclarer l, ArrayTypeDeclarer r) {
        if (l.literal()) return false;
        if (!l.element().equals(r.element()))
            return false;
        if (l.refer().has() ^ r.refer().has())
            return false;
        if (l.refer().has() && r.refer().has())
            return true;
        var ls = compute(l.length().must());
        var rs = compute(r.length().must());
        if (ls instanceof LiteralExpression le &&
                rs instanceof LiteralExpression re) {
            var ll = le.asInteger();
            var rl = re.asInteger();
            if (ll.has() && rl.has()) {
                return r.literal() ?
                        ll.get().compareTo(rl.get()) >= 0
                        : ll.equals(rl);
            }
        }
        return false;
    }

    private boolean referable(ClassDefinition l, ClassDefinition r) {
        visit(l.generic());
        visit(r.generic());

        if (r.same(l)) return true;
        if (r.parent().none()) return false;

        var pt = context.findType(r.parent().get().symbol());
        assert pt.has();
        if (pt.get() instanceof ClassDefinition pc)
            return referable(l, pc);

        return false;
    }

    private boolean referable(InterfaceDefinition l, ClassDefinition r) {
        visit(l.generic());
        visit(r.generic());

        if (r.impl().exists(l.symbol())) return true;

        for (var dt : r.impl().values()) {
            visit(dt.generic());
            var def = context.findType(dt.symbol());
            assert def.has();
            if (def.get() instanceof InterfaceDefinition id)
                if (referable(l, id)) return true;
        }

        return false;
    }

    private boolean referable(InterfaceDefinition l, InterfaceDefinition r) {
        visit(l.generic());
        visit(r.generic());

        if (l.same(r)) return true;

        for (var dt : r.parts().values()) {
            visit(dt.generic());
            var def = context.findType(dt.symbol());
            assert def.has();
            if (def.get() instanceof InterfaceDefinition id)
                if (referable(l, id)) return true;
        }

        return false;
    }

    private boolean referable(TypeDefinition l, TypeDefinition r) {
        if (l instanceof ClassDefinition lc) {
            if (r instanceof ClassDefinition rc)
                return referable(lc, rc);
            return false;
        }

        if (l instanceof InterfaceDefinition li) {
            if (r instanceof ClassDefinition rc) {
                return referable(li, rc);
            }
            if (r instanceof InterfaceDefinition ri) {
                return referable(li, ri);
            }
        }

        return false;
    }

    private boolean
    assignable(DefinedTypeDeclarer l, DefinedTypeDeclarer r) {
        if (l.refer().none() ^ r.refer().none())
            return false;

        assert l.definedType().generic().isEmpty() : "generic";
        assert r.definedType().generic().isEmpty() : "generic";

        var lt = context.findType(l.definedType().symbol()).must();
        var rt = context.findType(r.definedType().symbol()).must();
        if (l.refer().none())
            return lt.equals(rt);   // 变量为值类型就要求全相同

        if (r.refer().none()) {
            // 引用一个值类型
            return unsupported("refer a value: %s", r.pos());
        }

        var lr = l.refer().get();
        var rr = r.refer().get();
        if (lr.kind() == ReferKind.STRONG) {
            if (rr.kind() == ReferKind.STRONG) {
                return referable(lt, rt);
            }
        }

        // TODO: 这里很复杂！

        return l.definedType().equals(r.definedType());
    }

    private boolean assignable(TypeDeclarer l, TypeDeclarer r) {
        if (l instanceof PrimitiveTypeDeclarer pl &&
                r instanceof PrimitiveTypeDeclarer pr) {
            return pl.primitive() == pr.primitive();
        }

        if (l instanceof MemTypeDeclarer ml &&
                r instanceof MemTypeDeclarer mr) {
            if (!ml.readonly() && mr.readonly())
                return false;
            // TODO: 运行时边界检查
            return true;
        }

        if (l instanceof ArrayTypeDeclarer al &&
                r instanceof ArrayTypeDeclarer ar) {
            return assignable(al, ar);
        }

        if (l instanceof FuncTypeDeclarer fl &&
                r instanceof FuncTypeDeclarer fr) {
            return fl.prototype().equals(fr.prototype())
                    && fl.generic().equals(fr.generic());
        }

        if (l instanceof DefinedTypeDeclarer dl &&
                r instanceof DefinedTypeDeclarer dr) {
            return assignable(dl, dr);
        }

        return false;
    }

    private void assignable(List<TypeDeclarer> left,
                            List<TypeDeclarer> right,
                            Position pos) {
        if (left.size() != right.size())
            semantic("unaligned assignment: %s", pos);

        for (int i = 0; i < left.size(); i++) {
            var l = left.get(i);
            var r = right.get(i);
            if (!assignable(l, r))
                semantic("unassignable: %s = %s", l, r);
        }
    }

    @Override
    public Entity visit(AssignmentsStatement as) {
        var left = new ArrayList<TypeDeclarer>(as.operands().size());
        for (var ao : as.operands()) {
            visit(ao);
            left.add(typeDeducer.visit(ao.rhs()));
        }

        visit(as.tuple());
        var right = typeDeducer.visit(as.tuple()).tuple();
        assignable(left, right, as.pos());
        return as;
    }

    @Override
    public Entity visit(CallStatement e) {
        visit(e.call());
        return e;
    }

    @Override
    public Entity visit(LabeledStatement e) {
        return visit(e.statement());
    }

    private final Stack<ForStatement> loopStack = new Stack<>();

    @Override
    public Entity visit(BreakStatement e) {
        assert enterProc != null;
        if (e.label().has()) {
            if (enterProc.labels()
                    .contains(e.label().get()))
                return semantic("label not found: %s",
                        e.label().get());
        }

        if (loopStack.isEmpty()) semantic("out of loop");

        // TODO: 检查是否在循环中
        return e;
    }

    @Override
    public Entity visit(ContinueStatement e) {
        assert enterProc != null;
        if (e.label().has()) {
            if (enterProc.labels()
                    .contains(e.label().get()))
                return semantic("label not found: %s",
                        e.label().get());
        }

        if (loopStack.isEmpty()) semantic("out of loop");

        // TODO: 检查是否在循环中
        return e;
    }

    @Override
    public Entity visit(ForStatement e) {
        context.enterScope();
        loopStack.push(e);
        EntityVisitor.super.visit(e);
        loopStack.pop();
        context.exitScope();
        return e;
    }

    private void requireBool(Expression e) {
        var td = typeDeducer.visit(e);
        if (!(td instanceof PrimitiveTypeDeclarer ptd)
                || ptd.primitive() != Primitive.BOOL)
            semantic("condition must be bool");
    }

    @Override
    public Entity visit(ConditionalForStatement e) {
        e.initializer().use(this::visit);
        requireBool(e.condition());
        visit(e.condition());
        e.updater().use(this::visit);
        visit(e.body());
        return e;
    }

    @Override
    public Entity visit(IterableForStatement e) {
        return unsupported("iterable loop");
    }

    @Override
    public Entity visit(GotoStatement e) {
        return unsupported("goto");
    }

    @Override
    public Entity visit(IfStatement e) {
        context.enterScope();
        e.init().use(this::visit);
        requireBool(e.condition());
        visit(e.condition());
        visit(e.yes());
        e.not().use(this::visit);
        context.exitScope();
        return e;
    }

    @Override
    public Entity visit(ReturnStatement e) {
        assert enterProc != null;
        var rs = enterProc.prototype().returnSet();
        if (rs.isEmpty()) {
            if (e.result().none()) return e;
            return semantic("function no return");
        }
        if (e.result().none())
            return semantic("function has return");

        var tp = typeDeducer.visit(e.result().get()).tuple();
        assignable(rs, tp, e.pos());

        return e;
    }

    @Override
    public Entity visit(SwitchBranch e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(SwitchStatement e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(ThrowStatement e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(TryStatement e) {
        return EntityVisitor.super.visit(e);
    }


    //

    @Override
    public Entity visit(IndexAssignableOperand io) {
        var td = typeDeducer.visit(io.subject());
        if (!(td instanceof ArrayTypeDeclarer atd))
            return semantic("require array: %s", io.pos());
        if (atd.refer().has()) {
            if (atd.refer().get().immutable())
                return semantic("immutable array: %s", io.pos());
        } else {
            // TODO: 值类型检查const
            if (constChecker.visit(io.subject())) {
                return semantic("immutable array");
            }
        }

        var it = typeDeducer.visit(io.index());
        if (it instanceof PrimitiveTypeDeclarer ptd) {
            if (ptd.primitive().isInteger()) {
                return atd.element();
            }
        }
        return semantic("index require integer");
    }

    @Override
    public Entity visit(FieldAssignableOperand mo) {
        var m = typeDeducer.getMember(mo.subject(), mo.field());
        if (m instanceof StructureField sf) return sf.type();

        if (m instanceof ClassField cf) {
            if (cf.declare() != Declare.VAR)
                return semantic("const field: %s", mo.field().pos());
            var im = constChecker.visit(mo.subject());
            if (Boolean.TRUE.equals(im))
                return semantic("const variable: %s", mo.pos());
            return cf.type();
        }

        if (m instanceof ClassMethod)
            return semantic("can't modify method");

        return semantic("can't modify: %s", mo.field());
    }

    @Override
    public Entity visit(VariableAssignableOperand vo) {
        var s = vo.symbol();
        var v = context.findVar(s);
        if (v.none())
            return semantic("%s is not declared", s);

        var var = v.get();
        if (var.declare() == Declare.CONST)
            return semantic("%s is const operand", s);

        visit(var);
        return var.type().must();
    }

    @Override
    public Entity visit(Variable v) {
        visit(v.type().must());
        visit(v.modifier());
        return v;
    }

    //


    @Override
    public Entity visit(ArrayTuple e) {
        for (var v : e.values()) visit(v);
        return e;
    }

    @Override
    public Entity visit(IfTuple e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(ReturnTuple e) {
        visit(e.call());
        return e;
    }

    @Override
    public Entity visit(SwitchTuple e) {
        return EntityVisitor.super.visit(e);
    }

    @Override
    public Entity visit(BinaryExpression e) {
        visit(e.left());
        visit(e.right());
        return typeDeducer.visit(e);
    }

    @Override
    public Entity visit(UnaryExpression e) {
        visit(e.operand());
        return typeDeducer.visit(e);
    }

    @Override
    public Entity visit(AssertExpression e) {
        visit(e.subject());
        visit(e.type());
        var type = typeDeducer.visit(e.subject());
        if (assignable(e.type(), type))
            return e;

        return semantic("unassignable: %s", e.pos());
    }

    @Override
    public Entity visit(CallExpression e) {
        visit(e.callee());
        e.arguments().forEach(this::visit);

        var ctd = typeDeducer.visit(e.callee());
        if (!(ctd instanceof PrimitiveTypeDeclarer ||
                ctd instanceof FuncTypeDeclarer)) {
            return semantic("require callable: %s", e.pos());
        }
        var rtd = typeDeducer.visit(ctd);
        if (rtd instanceof VoidTypeDeclarer)
            return e;

        if (rtd instanceof FuncTypeDeclarer ftd) {
            if (!ftd.generic().isEmpty())
                return unsupported("generic");

            var left = ftd.prototype().parameterSet().types();
            var right = typeDeducer.visit(e.arguments());
            assignable(left, right, e.pos());
            return e;
        }

        if (rtd instanceof PrimitiveTypeDeclarer ptd) {
            // explicit convert
            if (e.arguments().size() != 1)
                return semantic("can only be one argument: %s", e.pos());
            var a = e.arguments().getFirst();
            var td = typeDeducer.visit(a);
            if (!(td instanceof PrimitiveTypeDeclarer atd))
                return semantic("can't convert: %s", a.pos());
            var rej = (ptd.primitive() == Primitive.BOOL) ^
                    (atd.primitive() == Primitive.BOOL);
            if (rej) return semantic("can't convert: %s", a.pos());
            return e;
        }

        return semantic("%s is callee or convertor", e.pos());
    }

    @Override
    public Entity visit(CurrentExpression e) {
        if (enterMethod != null)
            return e;
        return semantic("use %s outof method", e.name());
    }

    @Override
    public Entity visit(IndexOfExpression e) {
        visit(e.subject());
        visit(e.index());
        var st = typeDeducer.visit(e.subject());
        if (!(st instanceof ArrayTypeDeclarer))
            return semantic("only use for array: %s", e.pos());

        var it = typeDeducer.visit(e.index());
        if (typeDeducer.isInteger(it)) return e;

        return semantic("index reqiure integer: %s", e.pos());

    }

    @Override
    public Entity visit(LambdaExpression e) {
        return unsupported("lambda");
    }

    @Override
    public Entity visit(LiteralExpression e) {
        return e;
    }

    @Override
    public Entity visit(MemberOfExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");

        visit(e.subject());

        return typeDeducer.getMember(e.subject(), e.member());
    }

    @Override
    public Entity visit(NewArrayType e) {
        visit(e.element());
        visit(e.length());
        return e;
    }

    @Override
    public Entity visit(NewDefinedType e) {
        var def = visit(e.type());
        if (def instanceof ClassDefinition)
            return e;
        return semantic("require class: %s", e.type().pos());
    }

    @Override
    public Entity visit(NewMemType e) {
        visit(e.mapped());
        return e;
    }

    @Override
    public Entity visit(NewExpression e) {
        visit(e.type());
        e.init().use(this::visit);
        if (e.init().none()) return e;
        var left = typeDeducer.visit(e.type());
        if (e.type() instanceof NewArrayType na &&
                left instanceof ArrayTypeDeclarer at) {
            left = new ArrayTypeDeclarer(at.pos(), at.element(),
                    Optional.of(na.length()), Optional.empty());
        }
        var right = typeDeducer.visit(e.init().get());
        if (assignable(left, right)) return e;

        return semantic("new type and init type not match");
    }

    @Override
    public Entity visit(ArrayExpression ae) {
        visit(ae.elements());
        TypeDeclarer first = null;
        for (var el : ae.elements()) {
            var t = typeDeducer.visit(el);
            if (first == null) {
                first = t;
                continue;
            }
            if (!assignable(first, t))
                return semantic("array type mismatch: %s",
                        el.pos());
        }
        return ae;
    }

    @Override
    public Entity visit(ObjectExpression obj) {
        for (var e : obj.entries().values())
            visit(e);
        return obj;
    }

    @Override
    public Entity visit(PairsExpression e) {
        return unsupported("pairs");
    }

    @Override
    public Entity visit(ParenExpression e) {
        return visit(e.child());
    }

    @Override
    public Entity visit(ReferExpression e) {
        if (!e.generic().isEmpty())
            return unsupported("generic");

        var t = context.findType(e.symbol());
        if (t.has()) return e;

        var f = context.findFunc(e.symbol());
        if (f.has()) return e;

        var v = context.findVar(e.symbol());
        if (v.has()) return e;

        return semantic("undefined symbol: %s", e.symbol());
    }

    // attribute


    @Override
    public Entity visit(AttributeDefinition ad) {
        return ad;
    }
}
