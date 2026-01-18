package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.ast.var.FieldAssignableOperand;
import org.cossbow.feng.ast.var.IndexAssignableOperand;
import org.cossbow.feng.ast.var.VariableAssignableOperand;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.dag.DAGTask;
import org.cossbow.feng.util.DAGUtil;
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.util.Stack;
import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.visit.SymbolContext;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;

import static org.cossbow.feng.util.DAGUtil.bfsVisit;
import static org.cossbow.feng.util.DAGUtil.checkCyclic;
import static org.cossbow.feng.util.ErrorUtil.*;

public class SemanticAnalysis implements EntityVisitor<Entity> {

    private final StackedContext context;
    private final TypeDeducer typeDeducer;
    private final ConstChecker constChecker;
    private final TypeTool typeTool;

    public SemanticAnalysis(SymbolContext parent) {
        context = new StackedContext(parent);
        typeDeducer = new TypeDeducer(context);
        constChecker = new ConstChecker(context);
        typeTool = new TypeTool(context);
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

    private void visitInterface(IdentifierTable<TypeDefinition> types) {
        for (var t : types) {
            if (t instanceof InterfaceDefinition id) visit(id);
        }
    }

    private void visitClass(IdentifierTable<TypeDefinition> types) {
        for (var t : types) {
            if (t instanceof ClassDefinition cd) visit(cd);
        }
    }

    private void visitEnum(IdentifierTable<TypeDefinition> types) {
        for (var t : types) {
            if (t instanceof EnumDefinition ed) visit(ed);
        }
    }

    private void visitStructure(IdentifierTable<TypeDefinition> types) {
        for (var t : types) {
            if (t instanceof StructureDefinition sd) visit(sd);
        }
    }

    private void visitFunc(IdentifierTable<TypeDefinition> types) {
        for (var t : types) {
            if (t instanceof PrototypeDefinition fd) visit(fd);
        }
    }

    private void visitMem(IdentifierTable<TypeDefinition> types) {
        for (var t : types) {
            if (t instanceof MemDefinition md) visit(md);
        }
    }

    private void visitAttribute(IdentifierTable<TypeDefinition> types) {
        for (var t : types) {
            if (t instanceof AttributeDefinition ad) visit(ad);
        }
    }

    private Set<GlobalVariable> searchDependencies(
            IdentifierTable<GlobalVariable> global,
            Expression expr) {
        var deps = new HashSet<GlobalVariable>();
        var q = new ArrayDeque<Expression>();
        q.add(expr);
        while (!q.isEmpty()) {
            var c = q.poll();
            switch (c) {
                case ReferExpression e -> {
                    if (e.symbol().module().has())
                        break;
                    var v = global.tryGet(e.symbol().name());
                    if (v.has()) {
                        deps.add(v.get());
                        break;
                    }
                    semantic("var not declared: %s", e.symbol());
                }
                case BinaryExpression e -> {
                    q.add(e.left());
                    q.add(e.right());
                }
                case UnaryExpression e -> q.add(e.operand());
                case ArrayExpression e -> q.addAll(e.elements());

                case ObjectExpression e -> q.addAll(e.entries().values());
                case LiteralExpression e -> {
                }
                case NewExpression e -> {
                }
                case ParenExpression e -> q.add(e.child());
                case null, default -> semantic("can't use: %s", c);
            }
        }
        return deps;
    }

    private void visitGlobalVariable(
            IdentifierTable<GlobalVariable> global) {
        if (global.isEmpty()) return;
        var edges = new ArrayList<Groups.G2<GlobalVariable, GlobalVariable>>();
        for (var gv : global) {
            if (gv.init().none()) continue;
            var cyc = checkCyclic(gv, v -> {
                return searchDependencies(global, v.init().must());
            });
            if (cyc.has()) semantic("cyclic init: %s", cyc.get().symbol());
            var deps = searchDependencies(global, gv.init().must());
            for (var dep : deps)
                edges.add(Groups.g2(dep, gv));
        }
        var dag = new DAGGraph<>(global.values(), edges);
        new DAGTask<GlobalVariable, Boolean>(dag, (gv, args) -> {
            visit(gv);
            return CompletableFuture.completedFuture(true);
        }).results();
    }

    public Entity visit(Source s) {
        var tab = s.table();

        visitGlobalVariable(tab.variables);

        visitEnum(tab.namedTypes);
        visitStructure(tab.namedTypes);
        visitMem(tab.namedTypes);
        visitInterface(tab.namedTypes);
        visitClass(tab.namedTypes);
        visitFunc(tab.namedTypes);
        visitAttribute(tab.namedTypes);

        for (var t : tab.unnamedTypes) visit(t);

        for (var f : tab.namedFunctions) visit(f);

        return s;
    }

    public Entity visit(GlobalVariable gv) {
        if (gv.init().none()) {
            if (gv.declare() == Declare.CONST) {
                return semantic("const must init: %s", gv.pos());
            }
            // TODO: 值默认值
            return gv;
        }

        var et = typeDeducer.visit(gv.init().must());
        if (gv.type().has()) {
            if (!assignable(gv.type().must(), et))
                return semantic("can't assign init value: %s",
                        et.pos());
        } else {
            gv.type().set(et);
        }

        var e = compute(gv.init().must());
        if (!e.isFinal()) {
            return semantic("require final value: %s%s",
                    gv.name(), gv.pos());
        }
        gv.init().set(e);

        visit((Variable) gv);
        visit(gv.init().must());
        return gv;
    }

    public Entity visit(Modifier m) {
        if (!m.attributes().isEmpty())
            unsupported("attribute");
        return m;
    }

    public Entity visit(Refer e) {
        if (e.kind() == ReferKind.WEAK)
            unsupported("weak-reference");
        if (e.kind() == ReferKind.PHANTOM)
            unsupported("phantom-reference");
        if (e.required())
            unsupported("require reference");
        return e;
    }

    public Entity visit(TypeArguments ta) {
        if (ta.isEmpty()) return ta;
        return unsupported("generic");
    }

    public TypeDefinition visit(DefinedType dt) {
        if (!dt.generic().isEmpty())
            return unsupported("generic");
        var type = context.findType(dt.symbol());
        if (type.none())
            return semantic("%s not defined", dt.symbol());
        if (!type.get().generic().isEmpty())
            return unsupported("generic");
        return type.get();
    }

    public Entity visit(DefinedTypeDeclarer td) {
        var dt = visit(td.definedType());
        var r = td.refer();
        visit(r);

        if (dt instanceof ClassDefinition)
            return td;

        if (dt instanceof InterfaceDefinition) {
            if (r.has()) return td;
            return semantic("interface must be refer: %s",
                    td.pos());
        }

        if (r.none()) return td;
        return semantic("can't refer %s %s", dt.domain(), dt.symbol());
    }

    public Entity visit(ArrayTypeDeclarer td) {
        visit(td.element());
        td.length().use(this::visit);
        td.refer().use(this::visit);
        return td;
    }

    public Entity visit(FuncTypeDeclarer td) {
        visit(td.prototype(), false);
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

    public Entity visit(MemTypeDeclarer mtd) {
        if (mtd.mapped().none()) return mtd;
        var map = mtd.mapped().get();
        visit(map);
        if (checkMappable(map)) return mtd;
        return semantic("can't map type %s", map.pos());
    }

    public Entity visit(PrimitiveTypeDeclarer ptd) {
        return ptd;
    }

    public Entity visit(LiteralTypeDeclarer ltd) {
        return ltd;
    }

    public Entity visit(ThisTypeDeclarer ctd) {
        if (enterClass == null)
            semantic("this only use in class");
        return ctd;
    }

    public Entity visit(TupleTypeDeclarer ttd) {
        ttd.tuple().forEach(this::visit);
        return ttd;
    }

    public Entity visit(TypeParameters e) {
        if (e.isEmpty()) return e;
        return unsupported("generic");
    }

    public Entity visit(TypeDefinition def) {
        visit(def.modifier());
        visit(def.generic());
        EntityVisitor.super.visit(def);
        return def;
    }

    public Entity visit(PrimitiveDefinition e) {
        return e;
    }

    // structure define

    public Entity visit(StructureDefinition def) {
        if (!def.generic().isEmpty())
            return unsupported("generic");

        var cyc = checkCyclic(def, d -> {
            return d.fields().stream().map(StructureField::type)
                    .map(this::structFieldType)
                    .filter(Optional::has)
                    .map(Optional::get).toList();
        });
        if (cyc.has()) return semantic("%s cyclic extends: %s",
                def.symbol(), cyc.must().symbol());
        for (var f : def.fields())
            visit(f);

        return def;
    }

    private Optional<StructureDefinition> structFieldType(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            if (!ptd.primitive().isBool())
                return Optional.empty();
            return semantic("require integer or float: %s", ptd.pos());
        }

        if (td instanceof DefinedTypeDeclarer dtd) {
            if (dtd.refer().has()) {
                return semantic("can't be reference");
            }
            if (!dtd.definedType().generic().isEmpty()) {
                return unsupported("generic");
            }
            var def = visit(dtd.definedType());
            if (def instanceof StructureDefinition sd)
                return Optional.of(sd);

            return semantic("require structure type: %s",
                    dtd.definedType().pos());
        }

        if (td instanceof ArrayTypeDeclarer atd) {
            if (atd.refer().has()) {
                return semantic("can't be reference");
            }
            var len = computeConst(atd.length().must());
            var i = len.asInteger();
            if (i.none()) {
                return semantic("require integer");
            }
            if (i.get().value().compareTo(BigInteger.ZERO) < 0) {
                semantic("can't be negative: %s", atd.length().get().pos());
            }
            return structFieldType(atd.element());
        }

        return semantic("illegal type: %s%s", td, td.pos());
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

    public Entity visit(StructureField sf) {
        visit(sf.type());
        structFieldType(sf.type());

        if (sf.bitfield().has())
            structBitfield(sf.bitfield().get());

        return sf;
    }

    // enum

    public Entity visit(EnumDefinition def) {
        for (var v : def.values()) {
            if (v.init().none()) continue;
            var le = computeConst(v.init().must());

            var i = le.asInteger();
            if (i.none()) {
                return semantic("must const integer: %s",
                        v.pos());
            }
            v.init().set(le);
        }

        return def;
    }

    //

    private int compatible(Prototype l, Prototype r) {
        if (!l.parameterSet().equals(r.parameterSet()))
            return 1;
        if (!assignable(l.returnSet(), r.returnSet()))
            return 2;
        return 0;
    }

    // class define

    private void checkInherit(
            ClassDefinition parent, ClassDefinition child,
            IdentifierTable<ClassField> allFields,
            IdentifierTable<ClassMethod> allMethods) {
        // 检查同名属性
        for (var pf : parent.fields()) {
            var cf = allFields.tryGet(pf.name());
            if (cf.none()) {
                allFields.add(pf.name(), pf);
                continue;
            }
            semantic("can't hide parent field: %s%s",
                    pf.name(), cf.get().pos());
            return;
        }

        // 检查方法覆盖是否兼容
        for (var pm : parent.methods()) {
            var o = allMethods.tryGet(pm.name());
            if (o.none()) {
                allMethods.add(pm.name(), pm);
                continue;
            }
            var cm = o.must();
            if (pm.export() != cm.export()) {
                semantic("override require same export: ",
                        cm.pos());
                return;
            }
            var pp = pm.func().prototype();
            var cp = cm.func().prototype();
            var re = compatible(pp, cp);
            switch (re) {
                case 1 -> semantic("parameters not same: %s", cp.pos());
                case 2 -> semantic("returns not compatible: %s", cp.pos());
            }
        }

    }

    private Optional<ClassDefinition> findParent(ClassDefinition c) {
        var dt = visit(c.inherit().must());
        if (dt instanceof ClassDefinition pcd)
            return Optional.of(pcd);
        return semantic("require class: %s", dt.symbol());
    }

    private void checkAcyclicInherit(ClassDefinition cd) {
        var allFields = new IdentifierTable<>(cd.fields().nodes());
        var allMethods = new IdentifierTable<>(cd.methods().nodes());
        var set = new HashSet<Symbol>();
        var c = cd;
        while (c.inherit().has()) {
            if (!set.add(c.symbol()))
                semantic("inherit in cyclic: %s%s", c.symbol(), c.pos());

            var parent = findParent(c);
            if (parent.has()) {
                cd.parent().setIfNone(parent.must());

                checkInherit(parent.get(), cd, allFields, allMethods);

                c = parent.get();
                continue;
            }

            semantic("inherit must class: %s", cd.inherit().must().pos());
        }
        cd.allFields().set(allFields);
        cd.allMethods().set(allMethods);
    }

    private void checkImplList(InterfaceDefinition id, ClassDefinition cd) {
        var methods = cd.allMethods().must();
        for (var im : id.all().must()) {
            var cm = methods.tryGet(im.name());
            if (cm.none()) {
                semantic("%s unimplement method: %s%s",
                        cd.symbol(), im.name(), im.pos());
                return;
            }
            var re = compatible(im.prototype(), cm.must().prototype());
            switch (re) {
                case 1 -> semantic("unimplement method: %s", im.pos());
                case 2 -> semantic("returns not consistent: %s", im.pos());
            }
        }
    }

    private void checkImplList(ClassDefinition cd) {
        for (var dt : cd.impl()) {
            var td = visit(dt);
            if ((td instanceof InterfaceDefinition id)) {
                checkImplList(id, cd);
                continue;
            }
            semantic("require interface: %s", dt.pos());
        }
    }

    public Optional<ClassDefinition> classFieldType(TypeDeclarer t) {
        assert enterClass != null;

        if (t instanceof DefinedTypeDeclarer ctd) {
            if (ctd.refer().has())
                return Optional.empty();

            var dt = visit(ctd.definedType());
            if (dt instanceof ClassDefinition cd)
                return Optional.of(cd);
            return Optional.empty();
        }

        if (t instanceof ArrayTypeDeclarer atd) {
            if (atd.refer().has())
                return Optional.empty();
            return classFieldType(atd.element());
        }

        return Optional.empty();
    }

    private void checkAcyclicInit(ClassDefinition cd) {
        var cyc = checkCyclic(cd, d -> {
            return d.fields().stream().map(ClassField::type)
                    .map(this::classFieldType)
                    .filter(Optional::has)
                    .map(Optional::get)
                    .toList();
        });
        if (cyc.none()) return;
        semantic("cyclic init: %s",
                cyc.must().symbol());
    }

    private volatile ClassDefinition enterClass;
    private volatile ClassMethod enterMethod;

    public Entity visit(ClassDefinition cd) {
        assert enterClass == null;
        enterClass = cd;
        context.enterScope(cd);
        if (!cd.generic().isEmpty())
            return unsupported("generic");

        checkAcyclicInherit(cd);
        checkImplList(cd);
        checkAcyclicInit(cd);

        for (var f : cd.fields()) visit(f);
        for (var m : cd.methods()) visit(m);

        if (!cd.macros().isEmpty())
            unsupported("macro");
        context.exitScope();
        enterClass = null;
        return cd;
    }

    public Entity visit(ClassField cf) {
        assert enterClass != null;
        visit(cf.type());
        if (cf.type() instanceof DefinedTypeDeclarer ctd) {
            var isPhantom = ctd.refer().match(
                    r -> r.checkType(ReferKind.PHANTOM));
            if (!isPhantom) return visit(ctd);
            return semantic("can't be phantom-reference: %s",
                    ctd.pos());
        }
        return cf;
    }

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

    private List<InterfaceDefinition>
    findParts(InterfaceDefinition def) {
        return def.parts().stream().map(p -> {
            var t = visit(p);
            if (t instanceof InterfaceDefinition id) return id;
            return semantic("require interface: %s");
        }).toList();
    }

    private Optional<Groups.G2<InterfaceMethod, InterfaceMethod>>
    compatible(InterfaceDefinition part,
               Map<Identifier, InterfaceMethod> methods) {
        for (var m : part.methods()) {
            var name = m.name();
            var a = methods.putIfAbsent(name, m);
            if (a == null) continue;
            if (m.prototype().equals(a.prototype()))
                continue;
            return Optional.of(Groups.g2(m, a));
        }
        return Optional.empty();
    }

    public Entity visit(InterfaceDefinition def) {
        if (!def.generic().isEmpty()) return unsupported("generic");

        for (var m : def.methods()) visit(m);

        var cyc = checkCyclic(def, this::findParts);
        if (cyc.has()) return semantic("cyclic init: %s",
                cyc.must().symbol());


        var all = new HashMap<Identifier, InterfaceMethod>();
        for (var m : def.methods()) all.put(m.name(), m);
        bfsVisit(def, this::findParts, (d, p) -> {
            if (d == null) return;
            var c = compatible(p, all);
            if (c.none()) return;
            var g = c.must();
            semantic("duplicate method %s <--> %s",
                    g.a().pos(), g.b().pos());
        });
        def.all().set(new IdentifierTable<>(all));

        return def;
    }

    public Entity visit(InterfaceMethod m) {
        visit(m.generic());
        visit(m.prototype(), false);
        return m;
    }

    public Entity visit(PrototypeDefinition pd) {
        visit(pd.generic());
        visit(pd.prototype(), false);
        return pd;
    }

    //

    public Entity visit(FunctionDefinition fd) {
        visit(fd.generic());
        visit(fd.modifier());
        visit(fd.procedure());
        return fd;
    }

    private volatile Procedure enterProc;

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
        for (Variable v : ps.variables()) {
            visit(v);
            if (addVar) context.putVar(v);
        }
    }

    //

    public Entity visit(BlockStatement bs) {
        context.enterScope();
        for (var s : bs.list())
            visit(s);
        context.exitScope();
        return bs;
    }

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
                    if (t instanceof VoidTypeDeclarer)
                        return semantic("can't deduce type: %s", v.pos());

                    v.type().set(t);
                } else if (!assignable(v.type().must(), t)) {
                    return semantic("unassignable: %s", v.pos());
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

    private <F extends Field> boolean initializable(
            HaveFields<F> ld, ObjectTypeDeclarer od) {
        var obj = od.entries();

        for (var node : obj.nodes()) {
            var fo = ld.fields().tryGet(node.key());
            if (fo.none())
                return semantic("unknown field %s%s",
                        node.key(), node.key().pos());

            var able = assignable(fo.get().type(), node.value());
            if (!able) return false;
        }

        for (F f : ld.fields()) {
            if (f.immutable() && !obj.exists(f.name()))
                return semantic("const field must init: %s",
                        od.pos());
        }

        return true;
    }

    private boolean initializable(
            DefinedTypeDeclarer l, ObjectTypeDeclarer o) {
        var lt = visit(l.definedType());

        if (lt instanceof StructureDefinition def)
            return initializable(def, o);

        if (lt instanceof ClassDefinition def)
            return initializable(def, o);

        return false;
    }

    private boolean referable(
            StructureDefinition l, StructureDefinition r) {
        return l.equals(r);
    }

    private boolean
    assignable(ArrayTypeDeclarer l, ArrayTypeDeclarer r) {
        if (l.literal())
            return semantic("literal can't assign: %s", l.pos());

        if (!assignable(l.element(), r.element()))
            return false;

        if (l.refer().has() ^ r.refer().has())
            return semantic("value and refer can't assign");

        if (l.refer().has() && r.refer().has())
            return true;

        var ls = compute(l.length().must());
        var rs = compute(r.length().must());
        if (ls instanceof LiteralExpression le &&
                rs instanceof LiteralExpression re) {
            var ll = le.asInteger();
            var rl = re.asInteger();
            if (ll.has() && rl.has()) {
                if (!r.literal())
                    return ll.equals(rl);

                if (ll.get().compareTo(rl.get()) >= 0)
                    return true;

                return semantic("out of bound: %s",
                        re.pos());
            }
        }
        return false;
    }

    private boolean referable(ClassDefinition l, ClassDefinition r) {
        visit(l.generic());
        visit(r.generic());

        if (r.same(l)) return true;
        if (r.inherit().none()) return false;

        var pt = visit(r.inherit().get());
        if (pt instanceof ClassDefinition pc)
            return referable(l, pc);

        return false;
    }

    private boolean referable(InterfaceDefinition l, ClassDefinition r) {
        visit(l.generic());
        visit(r.generic());

        if (r.impl().exists(l.symbol())) return true;

        for (var dt : r.impl()) {
            visit(dt.generic());
            var def = visit(dt);
            if (def instanceof InterfaceDefinition id)
                if (referable(l, id)) return true;
        }

        return false;
    }

    private boolean referable(InterfaceDefinition l, InterfaceDefinition r) {
        visit(l.generic());
        visit(r.generic());

        if (l.equals(r)) return true;

        for (var dt : r.parts()) {
            visit(dt.generic());
            var def = visit(dt);
            if (def instanceof InterfaceDefinition id)
                if (referable(l, id)) return true;
        }

        return false;
    }

    private boolean referable(TypeDefinition l, TypeDefinition r) {
        if (l instanceof StructureDefinition ls) {
            if (r instanceof StructureDefinition rs)
                return referable(ls, rs);
            return false;
        }

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

        var lt = visit(l.definedType());
        var rt = visit(r.definedType());
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

        // TODO: 还有什么没考虑？虚引用！

        return l.definedType().equals(r.definedType());
    }

    private boolean assignable(
            PrimitiveTypeDeclarer l, LiteralTypeDeclarer r) {
        if (l.primitive().isInteger()) {
            return r.literal() instanceof IntegerLiteral;
        }

        if (l.primitive().isFloat()) {
            return r.literal() instanceof FloatLiteral;
        }

        if (l.primitive().isBool()) {
            return r.literal() instanceof BoolLiteral;
        }

        return false;
    }

    private boolean assignable(
            DefinedTypeDeclarer l, FuncTypeDeclarer r) {
        visit(r.generic());

        var dt = visit(l.definedType());
        if (!(dt instanceof PrototypeDefinition pd))
            return false;

        visit(pd.generic());

        var re = compatible(pd.prototype(), r.prototype());
        return switch (re) {
            case 1 -> semantic("parameters not match: %s", r.pos());
            case 2 -> semantic("returns not consistent: %s", r.pos());
            default -> true;
        };
    }

    private boolean assignable(TypeDeclarer l, TypeDeclarer r) {
        Objects.requireNonNull(l, "left");
        Objects.requireNonNull(r, "right");

        if (l.equals(r)) return true;

        if (l instanceof PrimitiveTypeDeclarer pl) {
            if (r instanceof PrimitiveTypeDeclarer pr)
                return pl.primitive() == pr.primitive();
            if (r instanceof LiteralTypeDeclarer lit)
                return assignable(pl, lit);
        }

        if (l instanceof MemTypeDeclarer ml) {
            if (r instanceof LiteralTypeDeclarer lit)
                return lit.literal() instanceof StringLiteral;

            if (r instanceof MemTypeDeclarer mr) {
                if (!ml.readonly() && mr.readonly())
                    return false;
            }
            // TODO: 运行时边界检查
            return true;
        }

        if (l instanceof ArrayTypeDeclarer al) {
            if (r instanceof ArrayTypeDeclarer ar)
                return assignable(al, ar);
            if (r instanceof LiteralTypeDeclarer lit)
                return lit.literal() instanceof NilLiteral
                        && al.refer().has();
            if (r instanceof VoidTypeDeclarer) {
                return al.refer().none();
            }
        }

        if (l instanceof FuncTypeDeclarer fl &&
                r instanceof FuncTypeDeclarer fr) {
            return fl.prototype().equals(fr.prototype())
                    && fl.generic().equals(fr.generic());
        }

        if (l instanceof DefinedTypeDeclarer dl) {
            if (r instanceof DefinedTypeDeclarer dr)
                return assignable(dl, dr);
            if (r instanceof ObjectTypeDeclarer o)
                return initializable(dl, o);
            if (r instanceof FuncTypeDeclarer f)
                return assignable(dl, f);
            if (dl.refer().has()) {
                if (r instanceof LiteralTypeDeclarer lit) {
                    return lit.literal() instanceof NilLiteral;
                }
            }
        }

        return false;
    }

    private boolean assignable(List<TypeDeclarer> left,
                               List<TypeDeclarer> right) {
        if (left.size() != right.size())
            return false;

        for (int i = 0; i < left.size(); i++) {
            var l = left.get(i);
            var r = right.get(i);
            if (!assignable(l, r))
                return false;
        }

        return true;
    }

    public Entity visit(AssignmentsStatement as) {
        var left = new ArrayList<TypeDeclarer>(as.operands().size());
        for (var ao : as.operands()) {
            var td = (TypeDeclarer) visit(ao);
            left.add(td);
        }

        visit(as.tuple());
        var right = typeDeducer.visit(as.tuple()).tuple();
        if (assignable(left, right)) return as;

        return semantic("type not compatible: %s", as.pos());
    }

    public Entity visit(CallStatement e) {
        visit(e.call());
        return e;
    }

    public Entity visit(LabeledStatement e) {
        return visit(e.statement());
    }

    private final Stack<ForStatement> loopStack = new Stack<>();

    public Entity visit(BreakStatement e) {
        assert enterProc != null;
        if (e.label().has()) {
            if (enterProc.labels()
                    .contains(e.label().get()))
                return semantic("label not found: %s",
                        e.label().get());
        }

        if (loopStack.isEmpty())
            return semantic("out of loop: %s", e.pos());

        return e;
    }

    public Entity visit(ContinueStatement e) {
        assert enterProc != null;
        if (e.label().has()) {
            if (enterProc.labels().contains(e.label().get()))
                return semantic("label not found: %s",
                        e.label().get());
        }

        if (loopStack.isEmpty())
            return semantic("out of loop: %s", e.pos());

        return e;
    }

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

    public Entity visit(ConditionalForStatement e) {
        e.initializer().use(this::visit);
        requireBool(e.condition());
        visit(e.condition());
        e.updater().use(this::visit);
        visit(e.body());
        return e;
    }

    public Entity visit(IterableForStatement e) {
        return unsupported("iterable loop");
    }

    public Entity visit(GotoStatement e) {
        return unsupported("goto");
    }

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

    public Entity visit(ReturnStatement e) {
        assert enterProc != null;
        var rs = enterProc.prototype().returnSet();
        if (rs.isEmpty()) {
            if (e.result().none()) return e;
            return semantic("no return");
        }
        if (e.result().none())
            return semantic("has return");

        var tp = typeDeducer.visit(e.result().get()).tuple();
        if (assignable(rs, tp)) return e;
        return semantic("return not compatible: %s", e.pos());
    }

    public Entity visit(SwitchBranch e) {
        for (var ce : e.constants()) {
            var le = computeConst(ce);
            if (le.literal() instanceof IntegerLiteral ||
                    le.literal() instanceof BoolLiteral)
                continue;
            return semantic("switch case constants need intger or bool",
                    ce.pos());
        }

        for (var s : e.statements()) visit(s);

        return e;
    }

    public Entity visit(SwitchStatement e) {
        visit(e.init());
        var td = typeDeducer.visit(e.value());
        if (!typeDeducer.isInteger(td) && !typeDeducer.isBool(td)) {
            return semantic("switch value need intger or bool",
                    e.value().pos());
        }
        visit(e.value());

        for (var br : e.branches()) visit(br);

        for (var s : e.defaultBranch()) visit(s);

        return e;
    }

    public Entity visit(ThrowStatement e) {
        return EntityVisitor.super.visit(e);
    }

    public Entity visit(TryStatement e) {
        return EntityVisitor.super.visit(e);
    }


    //

    public Entity visit(IndexAssignableOperand io) {
        var td = typeDeducer.visit(io.subject());
        if (!(td instanceof ArrayTypeDeclarer atd))
            return semantic("require array: %s", io.pos());
        if (atd.refer().has()) {
            if (atd.refer().get().immutable())
                return semantic("immutable array: %s", io.pos());
        } else {
            if (constChecker.visit(io.subject())) {
                return semantic("immutable array");
            }
        }

        var it = typeDeducer.visit(io.index());
        if (it instanceof PrimitiveTypeDeclarer ptd) {
            if (ptd.primitive().isInteger())
                return atd.element();
        } else if (it instanceof LiteralTypeDeclarer ltd) {
            if (ltd.literal() instanceof IntegerLiteral) {
                return atd.element();
            }
        }

        return semantic("forbidden index operate: %s", io.pos());
    }

    public Entity visit(FieldAssignableOperand e) {
        var g2 = typeTool.getField(e.subject(), e.field());
        if (g2.none()) return semantic("field not defined: %s",
                e.field().pos());

        var refer = g2.must().a().maybeRefer();
        var f = g2.must().b();
        if (f instanceof StructureField sf) {
            if (refer.has()) return sf.type();

            var im = constChecker.visit(e.subject());
            if (im) return semantic("const variable: %s", e.pos());
            return sf.type();
        }

        if (f instanceof ClassField cf) {
            if (cf.declare() != Declare.VAR)
                return semantic("const field: %s", e.field().pos());

            if (refer.has()) return cf.type();

            var im = constChecker.visit(e.subject());
            if (im) return semantic("const variable: %s", e.pos());

            return cf.type();
        }

        return unreachable();
    }

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

    public Entity visit(Variable v) {
        visit(v.type().must());
        visit(v.modifier());
        return v;
    }

    //


    public Entity visit(ArrayTuple e) {
        for (var v : e.values()) visit(v);
        return e;
    }

    public Entity visit(IfTuple e) {
        return EntityVisitor.super.visit(e);
    }

    public Entity visit(ReturnTuple e) {
        visit(e.call());
        return e;
    }

    public Entity visit(SwitchTuple e) {
        return EntityVisitor.super.visit(e);
    }

    public Entity visit(BinaryExpression e) {
        visit(e.left());
        visit(e.right());
        return typeDeducer.visit(e);
    }

    public Entity visit(UnaryExpression e) {
        visit(e.operand());
        return typeDeducer.visit(e);
    }

    public Entity visit(AssertExpression e) {
        visit(e.subject());
        visit(e.type());
        var type = typeDeducer.visit(e.subject());
        if (assignable(e.type(), type))
            return e;

        return semantic("unassignable: %s", e.pos());
    }

    public Entity visit(CallExpression e) {
        visit(e.callee());
        e.arguments().forEach(this::visit);

        var rtd = typeDeducer.visit(e);

        if (rtd instanceof FuncTypeDeclarer ftd) {
            if (!ftd.generic().isEmpty())
                return unsupported("generic");

            var left = ftd.prototype().parameterSet().types();
            var right = typeDeducer.visit(e.arguments());
            if (assignable(left, right)) return e;
            return semantic("arguments not compatible");
        }

        if (rtd instanceof PrimitiveTypeDeclarer ptd) {
            // explicit convert
            if (e.arguments().size() != 1)
                return semantic("convert : %s", e.pos());
            var a = e.arguments().getFirst();
            var td = typeDeducer.visit(a);
            if (td instanceof PrimitiveTypeDeclarer atd) {
                if (ptd.primitive().isBool() == atd.primitive().isBool())
                    return e;
            } else if (td instanceof LiteralTypeDeclarer lit) {
                if (ptd.primitive().isInteger() &&
                        lit.literal() instanceof IntegerLiteral)
                    return e;

                if (ptd.primitive().isFloat() &&
                        lit.literal() instanceof FloatLiteral)
                    return e;

                if (ptd.primitive().isBool() &&
                        lit.literal() instanceof BoolLiteral)
                    return e;
            }

            return semantic("can't convert: %s", a.pos());
        }

        if (rtd instanceof VoidTypeDeclarer) return e;

        return semantic("%s is callee or convertor", e.pos());
    }

    public Entity visit(CurrentExpression e) {
        if (enterMethod != null)
            return e;
        return semantic("use %s outof method", e.name());
    }

    public Entity visit(IndexOfExpression e) {
        visit(e.subject());
        visit(e.index());
        var st = typeDeducer.visit(e.subject());
        if (!(st instanceof ArrayTypeDeclarer))
            return semantic("only use for array: %s", e.pos());

        var it = typeDeducer.visit(e.index());
        if (typeDeducer.isInteger(it)) return e;

        return semantic("index require integer: %s", e.pos());

    }

    public Entity visit(LambdaExpression e) {
        return unsupported("lambda");
    }

    public Entity visit(LiteralExpression e) {
        return e;
    }

    public Entity visit(MemberOfExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");
        visit(e.subject());

        var ev = typeTool.getEnum(e.subject(), e.member());
        if (ev.has()) return ev.must().b();

        var f = typeTool.getField(e.subject(), e.member());
        if (f.has()) return f.must().b();

        var m = typeTool.getMethod(e.subject(), e.member());
        if (m.has()) return m.must();

        return unreachable();
    }

    public Entity visit(NewArrayType e) {
        visit(e.element());
        visit(e.length());
        return e;
    }

    public Entity visit(NewDefinedType e) {
        var def = visit(e.type());
        if (def instanceof ClassDefinition)
            return e;
        return semantic("require class: %s", e.type().pos());
    }

    public Entity visit(NewMemType e) {
        visit(e.mapped());
        return e;
    }

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

    public Entity visit(ArrayExpression ae) {
        visit(ae.elements());
        return ae;
    }

    public Entity visit(ObjectExpression obj) {
        for (var e : obj.entries())
            visit(e);
        return obj;
    }

    public Entity visit(PairsExpression e) {
        return unsupported("pairs");
    }

    public Entity visit(ParenExpression e) {
        return visit(e.child());
    }

    public Entity visit(ReferExpression e) {
        if (!e.generic().isEmpty())
            return unsupported("generic");

        return typeDeducer.visit(e);
    }

    // attribute


    public Entity visit(AttributeDefinition ad) {
        return ad;
    }
}
