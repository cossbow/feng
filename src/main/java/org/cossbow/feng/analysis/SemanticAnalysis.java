package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.lit.BoolLiteral;
import org.cossbow.feng.ast.lit.FloatLiteral;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.lit.NilLiteral;
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
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.util.Stack;
import org.cossbow.feng.visit.EntityVisitor;
import org.cossbow.feng.visit.SymbolContext;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.cossbow.feng.ast.dcl.ReferKind.*;
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

    private IntegerLiteral computeConstInteger(Expression e) {
        var r = computeConst(e);
        if (r.literal() instanceof IntegerLiteral il)
            return il;
        return semantic("require const integer: %s", e.pos());
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
                case NewExpression e -> e.arg().use(q::add);
                case ParenExpression e -> q.add(e.child());
                case LiteralExpression e -> {
                }
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
            if (gv.value().none()) continue;
            var cyc = checkCyclic(gv, v -> {
                return searchDependencies(global, v.value().must());
            });
            if (cyc.has()) semantic("cyclic init: %s", cyc.get().symbol());
            var deps = searchDependencies(global, gv.value().must());
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
        if (gv.value().none()) {
            if (gv.declare() != Declare.CONST)
                return visit((Variable) gv);
            return semantic("const must init: %s", gv.pos());
        }

        var et = typeDeducer.visit(gv.value().must());
        if (gv.type().has()) {
            if (!assignable(gv.type().must(), et, Optional.empty()))
                return semantic("can't assign init value: %s",
                        et.pos());
        } else {
            gv.type().set(et);
        }

        var e = compute(gv.value().must());
        if (!e.isFinal()) {
            return semantic("require final value: %s%s",
                    gv.name(), gv.pos());
        }
        gv.value().set(e);

        visit((Variable) gv);
        visit(gv.value().must());
        return gv;
    }

    public Entity visit(Modifier m) {
        if (!m.attributes().isEmpty())
            unsupported("attribute");
        return m;
    }

    public Entity visit(Refer e) {
        if (e.kind() == WEAK)
            unsupported("weak-reference");
        if (arrayStack.isEmpty()) return e;
        if (e.kind() != PHANTOM) return e;
        semantic("array element can't be phantom-reference: %s", e.pos());
        return e;
    }

    public Entity visit(TypeArguments ta) {
        if (ta.isEmpty()) return ta;
        return unsupported("generic");
    }

    public TypeDefinition visit(DerivedType dt) {
        if (!dt.generic().isEmpty())
            return unsupported("generic");
        var type = context.findType(dt.symbol());
        if (type.none())
            return semantic("type %s not defined", dt.symbol());
        if (!type.get().generic().isEmpty())
            return unsupported("generic");
        return type.get();
    }

    @Override
    public Entity visit(MemType t) {
        if (t.mapped().none()) return t;

        visit(t.mapped().get());

        return t;
    }

    @Override
    public Entity visit(PrimitiveType t) {
        return t;
    }

    //

    public TypeDefinition visit(DerivedTypeDeclarer td) {
        var dt = visit(td.derivedType());
        var r = td.refer();
        visit(r);

        if (dt instanceof ClassDefinition)
            return dt;

        if (dt instanceof InterfaceDefinition) {
            if (r.has()) return dt;
            return semantic("interface must be refer: %s",
                    td.pos());
        }

        if (r.none()) return dt;
        return semantic("can't refer %s %s", dt.domain(), dt.symbol());
    }

    private final Stack<Entity> arrayStack = new Stack<>();

    public Entity visit(ArrayTypeDeclarer td) {
        arrayStack.push(td);
        visit(td.element());
        td.length().use(this::visit);
        if (!td.refer().none()) {
            visit(td.refer().get());
        }
        arrayStack.pop();
        return td;
    }

    public Entity visit(FuncTypeDeclarer td) {
        visit(td.prototype(), false);
        visit(td.generic());
        return td;
    }

    private boolean checkMappable(DerivedTypeDeclarer dtd) {
        if (dtd.refer().has()) return
                semantic("can't map refer: %s", dtd.pos());
        var type = visit(dtd.derivedType());
        if (type instanceof StructureDefinition) return true;
        return semantic("can't map %s: %s", type, dtd.pos());
    }

    private boolean checkMappable(ArrayTypeDeclarer td) {
        if (td.refer().has()) return
                semantic("can't map refer: %s", td.pos());
        return checkMappable(td.element());
    }

    private boolean checkMappable(TypeDeclarer td) {
        return switch (td) {
            case PrimitiveTypeDeclarer ignored -> true;
            case DerivedTypeDeclarer dtd -> checkMappable(dtd);
            case ArrayTypeDeclarer atd -> checkMappable(atd);
            case null -> unreachable();
            default -> semantic("can't map type %s", td.pos());
        };
    }

    public Entity visit(MemTypeDeclarer mtd) {
        if (mtd.mapped().none()) return mtd;
        var map = mtd.mapped().get();
        visit(map);
        if (checkMappable(map)) {
            checkMapArraySize(map, true, true);
            return mtd;
        }
        return semantic("can't map type %s", map.pos());
    }

    public Entity visit(PrimitiveTypeDeclarer ptd) {
        return ptd;
    }

    public Entity visit(LiteralTypeDeclarer ltd) {
        return ltd;
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

        if (td instanceof DerivedTypeDeclarer dtd) {
            if (dtd.refer().has())
                return semantic("can't be reference");

            if (!dtd.derivedType().generic().isEmpty())
                return unsupported("generic");

            var def = visit(dtd.derivedType());
            if (def instanceof StructureDefinition sd)
                return Optional.of(sd);

            return semantic("require structure type: %s",
                    dtd.derivedType().pos());
        }

        if (td instanceof ArrayTypeDeclarer atd) {
            if (atd.refer().has()) {
                return semantic("can't be reference");
            }
            var i = computeConstInteger(atd.length().must());
            if (i.value().compareTo(BigInteger.ZERO) < 0) {
                semantic("can't be negative: %s", atd.length().get().pos());
            }
            return structFieldType(atd.element());
        }

        return semantic("illegal type: %s%s", td, td.pos());
    }

    private void structBitfield(PrimitiveTypeDeclarer td, Expression bf) {
        var il = computeConstInteger(bf);

        var v = il.value().intValue();
        if (0 < v && v <= td.primitive().width)
            return;

        semantic("bitfield must in range [1,64]: %s", bf.pos());
    }

    public Entity visit(StructureField sf) {
        structFieldType(sf.type());

        sf.bitfield().use(bf -> {
            if (sf.type() instanceof PrimitiveTypeDeclarer ptd) {
                structBitfield(ptd, bf);
                return;
            }
            semantic("bitfield only for primitive: %s", bf.pos());
        });

        return sf;
    }

    // enum

    public Entity visit(EnumDefinition def) {
        var i = 0;
        for (var v : def.values()) {
            v.code(i++);
            if (v.init().none()) {
                continue;
            }
            var il = computeConstInteger(v.init().must());
            v.init().set(new LiteralExpression(il.pos(), il));
            v.code(il.value().intValue());
        }

        return def;
    }

    //

    private int compatible(Prototype l, Prototype r) {
        if (!l.parameterSet().equals(r.parameterSet()))
            return 1;
        if (!assignable(l.returnSet(), r.returnSet(), List.of()))
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

    private ClassDefinition findParent(DerivedType t) {
        var dt = visit(t);
        if (dt instanceof ClassDefinition pcd)
            return pcd;
        return semantic("require class: %s", dt.symbol());
    }

    private void checkAllInherits(ClassDefinition cd) {
        var allFields = new IdentifierTable<>(cd.fields().nodes());
        var allMethods = new IdentifierTable<>(cd.methods().nodes());
        var set = new HashSet<Symbol>();
        var c = cd;
        while (c.inherit().has()) {
            if (!set.add(c.symbol()))
                semantic("inherit in cyclic: %s%s", c.symbol(), c.pos());

            var parent = findParent(c.inherit().must());
            cd.parent().setIfNone(parent);
            checkInherit(parent, cd, allFields, allMethods);
            c = parent;
        }
        cd.allFields().addAll(allFields);
        cd.allMethods().addAll(allMethods);
    }

    private void checkImplList(InterfaceDefinition id, ClassDefinition cd) {
        for (var im : id.all()) {
            var cm = cd.allMethods().tryGet(im.name());
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

    private Optional<ClassDefinition>
    getClassTypeField(TypeDeclarer t) {
        assert enterClass != null;

        if (t instanceof DerivedTypeDeclarer ctd) {
            if (ctd.refer().has())
                return Optional.empty();

            var dt = visit(ctd.derivedType());
            if (dt instanceof ClassDefinition cd)
                return Optional.of(cd);
            return Optional.empty();
        }

        if (t instanceof ArrayTypeDeclarer atd) {
            if (atd.refer().has())
                return Optional.empty();
            return getClassTypeField(atd.element());
        }

        return Optional.empty();
    }

    private void checkAcyclicInit(ClassDefinition cd) {
        var cyc = checkCyclic(cd, d -> {
            var inherit = d.inherit().stream().map(this::findParent);
            var fields = d.fields().stream().map(ClassField::type)
                    .map(this::getClassTypeField).flatMap(Optional::stream);
            return Stream.concat(inherit, fields)
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

        checkAcyclicInit(cd);
        checkAllInherits(cd);
        checkImplList(cd);

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
        if (cf.type() instanceof DerivedTypeDeclarer ctd) {
            var isPhantom = ctd.refer().match(
                    r -> r.isKind(PHANTOM));
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
        all.forEach((k, v) -> def.all().add(k, v));

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

    private <T> T missingReturn() {
        return semantic("Missing return statement: %s",
                enterProc.pos());
    }

    private boolean isEndStmt(Statement s) {
        return s instanceof ReturnStatement
                || s instanceof ThrowStatement;
    }

    private Void checkAllPathReturn(Procedure proc) {
        if (!proc.prototype().returnSet().isEmpty()) return null;
        var analyzer = new ReturnAnalyzer(this::isEndStmt);
        if (analyzer.check(proc.body())) return null;
        return missingReturn();
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
            if (td instanceof DerivedTypeDeclarer dtd) {
                var isWeak = dtd.refer().match(
                        r -> r.isKind(WEAK));
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
        if (bs.newScope()) context.enterScope();
        for (var s : bs.list())
            visit(s);
        if (bs.newScope()) context.exitScope();
        return bs;
    }

    private void initVar(List<Variable> variables, Tuple init) {
        visit(init);
        var td = typeDeducer.visit(init);
        var types = td.tuple();
        if (types.size() != variables.size()) {
            semantic("unaligned declaration: %s", init.pos());
            return;
        }
        for (int i = 0; i < variables.size(); i++) {
            var v = variables.get(i);
            var t = types.get(i);
            if (v.type().none()) {
                if (init instanceof ArrayTuple at) {
                    v.value().set(compute(at.values().get(i)));
                }
                if (t instanceof VoidTypeDeclarer) {
                    semantic("can't deduce type: %s", v.pos());
                    return;
                }
                v.type().set(t);
            } else {
                if (init instanceof ArrayTuple at) {
                    var iv = compute(at.values().get(i));
                    v.value().set(iv);
                    if (!assignable(v.type().must(), t, Optional.of(iv))) {
                        semantic("init value not assignable : %s", v.pos());
                        return;
                    }
                } else {
                    if (!assignable(v.type().must(), t, Optional.empty())) {
                        semantic("init value not assignable : %s", v.pos());
                        return;
                    }
                }
            }

        }
    }

    public Entity visit(DeclarationStatement ds) {
        if (ds.init().has()) {
            initVar(ds.variables(), ds.init().must());
        } else {
            for (var v : ds.variables()) {
                if (v.declare() != Declare.CONST)
                    continue;
                return semantic("const must init: %s", v.pos());
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

            var able = assignable(fo.get().type(), node.value(), Optional.empty());
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
            DerivedTypeDeclarer l, ObjectTypeDeclarer o) {
        var lt = visit(l.derivedType());

        if (lt instanceof StructureDefinition def)
            return initializable(def, o);

        if (lt instanceof ClassDefinition def)
            return initializable(def, o);

        return false;
    }

    private boolean assignable(
            StructureDefinition l, StructureDefinition r) {
        return l.equals(r);
    }

    private boolean
    assignable(ArrayTypeDeclarer l, ArrayTypeDeclarer r) {
        if (l.literal())
            return semantic("literal can't assign: %s", l.pos());

        if (!assignable(l.element(), r.element(), Optional.empty()))
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

    private boolean assignable(ClassDefinition l, ClassDefinition r) {
        visit(l.generic());
        visit(r.generic());

        if (r.same(l)) return true;
        if (r.inherit().none()) return false;

        var pt = visit(r.inherit().get());
        if (pt instanceof ClassDefinition pc)
            return assignable(l, pc);

        return false;
    }

    private boolean assignable(InterfaceDefinition l, ClassDefinition r) {
        visit(l.generic());
        visit(r.generic());

        if (r.impl().exists(l.symbol())) return true;

        var ok = r.inherit().map(this::findParent)
                .match(p -> assignable(l, p));
        if (ok) return true;

        for (var dt : r.impl()) {
            visit(dt.generic());
            var id = (InterfaceDefinition) visit(dt);
            if (assignable(l, id)) return true;
        }

        return false;
    }

    private boolean assignable(InterfaceDefinition l, InterfaceDefinition r) {
        visit(l.generic());
        visit(r.generic());

        if (l.equals(r)) return true;

        for (var dt : r.parts()) {
            visit(dt.generic());
            var def = visit(dt);
            if (def instanceof InterfaceDefinition id)
                if (assignable(l, id)) return true;
        }

        return false;
    }

    private boolean assignable(TypeDefinition l, TypeDefinition r) {
        if (l instanceof StructureDefinition ls) {
            if (r instanceof StructureDefinition rs)
                return assignable(ls, rs);
            return false;
        }

        if (l instanceof ClassDefinition lc) {
            if (r instanceof ClassDefinition rc)
                return assignable(lc, rc);
            return false;
        }

        if (l instanceof InterfaceDefinition li) {
            if (r instanceof ClassDefinition rc) {
                return assignable(li, rc);
            }
            if (r instanceof InterfaceDefinition ri) {
                return assignable(li, ri);
            }
        }

        return false;
    }

    private boolean
    assignable(DerivedTypeDeclarer l, DerivedTypeDeclarer r,
               Optional<Expression> re) {

        assert l.derivedType().generic().isEmpty() : "generic";
        assert r.derivedType().generic().isEmpty() : "generic";

        var lt = visit(l.derivedType());
        var rt = visit(r.derivedType());
        if (l.refer().none())
            return r.refer().none() && lt.equals(rt);

        var lr = l.refer().get();

        if (lr.kind() == PHANTOM) {
            var ok = re.match(e ->
                    new PhantomChecker(context).checkEnable(e));
            return ok && assignable(lt, rt);
        }
        if (lr.kind() == STRONG) {
            return r.refer().has() &&
                    r.refer().get().isKind(STRONG)
                    && assignable(lt, rt);
        }

        assert l.refer().none();
        assert r.refer().none();
        return l.derivedType().equals(r.derivedType());
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
            DerivedTypeDeclarer l, FuncTypeDeclarer r) {
        visit(r.generic());

        var dt = visit(l.derivedType());
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

    private boolean assignable(
            MemTypeDeclarer l, TypeDeclarer r, Optional<Expression> re) {
        if (r instanceof LiteralTypeDeclarer lit) {
            if (lit.isNil())
                return true;

            if (lit.isString()) {
                if (l.readonly())
                    return !l.ref().isKind(PHANTOM);

                return semantic("refer string-literal must rom: %s",
                        lit.pos());
            }

            return semantic("only can be nil or refer string-literal: %s",
                    lit.pos());
        }

        if (r instanceof MemTypeDeclarer mr) {
            return l.readonly() || !mr.readonly();
        }
        // TODO: check refer

        return false;
    }

    private boolean assignable(
            TypeDeclarer l, TypeDeclarer r, Optional<Expression> re) {
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
            return assignable(ml, r, re);
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

        if (l instanceof DerivedTypeDeclarer dl) {
            if (r instanceof DerivedTypeDeclarer dr)
                return assignable(dl, dr, re);
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
                               List<TypeDeclarer> right,
                               List<Expression> es) {
        if (left.size() != right.size() || (!es.isEmpty() && es.size() != left.size()))
            return semantic("assign must align");

        for (int i = 0; i < left.size(); i++) {
            var l = left.get(i);
            var r = right.get(i);
            var e = es.isEmpty() ? null : es.get(i);
            if (!assignable(l, r, Optional.of(e)))
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
        if (assignable(left, right, List.of())) return as;

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

    private void checkLabel(Identifier label) {
        assert enterProc != null;
        if (enterProc.labels().contains(label)) return;
        semantic("label not found: %s", label.pos());
    }

    public Entity visit(BreakStatement e) {
        e.label().use(this::checkLabel);

        if (loopStack.isEmpty())
            return semantic("out of loop: %s", e.pos());

        return e;
    }

    public Entity visit(ContinueStatement e) {
        e.label().use(this::checkLabel);

        if (loopStack.isEmpty())
            return semantic("out of loop: %s", e.pos());

        return e;
    }

    public Entity visit(ForStatement e) {
        loopStack.push(e);
        context.enterScope();
        EntityVisitor.super.visit(e);
        context.exitScope();
        loopStack.pop();
        return e;
    }

    private void requireBool(Expression e) {
        var td = typeDeducer.visit(e);
        if (typeDeducer.isBool(td))
            return;

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
        checkLabel(e.label());
        return e;
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
        if (assignable(rs, tp, List.of())) return e;
        return semantic("return not compatible: %s", e.pos());
    }

    private void checkConstantInteger(SwitchStatement s) {
        var set = new UniqueTable<IntegerLiteral, IntegerLiteral>();
        for (var b : s.branches()) {
            for (var c : b.constants()) {
                var il = computeConstInteger(c);
                set.add(il, il);
                continue;
            }
        }
        if (s.defaultBranch().has()) return;
        semantic("enum integer must add 'default': %s", s.pos());
    }

    private void checkConstantEnum(
            SwitchStatement s, EnumDefinition type) {
        var set = new UniqueTable<Identifier, Identifier>();
        for (var b : s.branches()) {
            for (var c : b.constants()) {
                if (c instanceof ReferExpression le) {
                    if (!le.generic().isEmpty()) {
                        semantic("generic");
                        return;
                    }
                    if (le.symbol().module().none()) {
                        var name = le.symbol().name();
                        var v = type.values().tryGet(name);
                        if (v.has()) {
                            set.add(name, name);
                            continue;
                        }
                    }
                }
                semantic("must value of enum %s: %s",
                        type.symbol(), c.pos());
                return;
            }
        }
        if (s.defaultBranch().has())
            return;

        for (var ev : type.values()) {
            if (set.exists(ev.name())) continue;
            semantic("must cover all values or add 'default': %s",
                    s.value().pos());
        }
    }

    public Entity visit(SwitchStatement e) {
        context.enterScope();
        visit(e.init());

        var td = typeDeducer.visit(e.value());
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            if (!ptd.primitive().isInteger())
                return semantic("must be integer: %s", e.value().pos());
            checkConstantInteger(e);
        } else if (td instanceof DerivedTypeDeclarer dtd) {
            var dt = visit(dtd.derivedType());
            if (!(dt instanceof EnumDefinition ed))
                return semantic("must be enum: %s", e.value().pos());
            checkConstantEnum(e, ed);
        } else {
            return semantic("value require integer or enum: %s",
                    e.value().pos());
        }

        for (var br : e.branches()) visit(br.body());

        e.defaultBranch().use(br -> visit(br.body()));

        context.exitScope();
        return e;
    }

    public Entity visit(ThrowStatement e) {
        return visit(e.exception());
    }

    public Entity visit(TryStatement e) {
        context.enterScope();
        visit(e.body());
        context.exitScope();
        visit(e.catchClauses());
        visit(e.finallyClause());
        return e;
    }

    public Entity visit(CatchClause e) {
        context.enterScope();
        var arg = e.argument();
        if (e.typeSet().size() > 1) {
            // TODO: 推导
            return unsupported("catch multi types: %s",
                    e.typeSet().get(1).pos());
        } else {
            arg.type().set(e.typeSet().getFirst());
        }
        visit(e.argument());
        visit(e.typeSet());
        context.putVar(e.argument());
        visit(e.body());
        context.exitScope();
        return e;
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

        var s = g2.must().a();
        var f = g2.must().b();
        if (f instanceof StructureField sf) {
            if (s instanceof MemTypeDeclarer mtd) {
                if (!mtd.readonly())
                    return sf.type();
                return semantic("rom can't write: %s",
                        e.pos());
            }
            assert s instanceof DerivedTypeDeclarer;

            var im = constChecker.visit(e.subject());
            if (im) return semantic("const variable: %s", e.pos());
            return sf.type();
        }

        if (f instanceof ClassField cf) {
            if (cf.declare() != Declare.VAR)
                return semantic("const field: %s", e.field().pos());

            if (s.maybeRefer().has()) return cf.type();

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

    public Entity visit(ReturnTuple e) {
        visit(e.call());
        return e;
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
        var st = typeDeducer.visit(e.subject());
        var sr = st.maybeRefer();
        if (sr.none()) return semantic(
                "require a refer: %s", e.subject().pos());

        var et = e.type();
        var er = et.maybeRefer();
        if (er.none()) return semantic(
                "type must refer: %s", e.type().pos());

        if (sr.get().kind() != er.get().kind()) return
                semantic("can't change refer kind from '%s' to '%s': %s",
                        st, et, e.type().pos());

        var sot = typeTool.getObject(st);
        var eot = typeTool.getObject(et);
        if (sot.none() || eot.none())
            return semantic("require class or interface refer: %s",
                    e.pos());

        if (assignable(eot.get(), sot.get()))
            return e;
        if (assignable(sot.get(), eot.get()))
            return e;

        return semantic("inconvertible types %s to %s: %s",
                st, et, e.pos());
    }

    private Entity checkConvertor(
            ConvertorTypeDeclarer ctd, CallExpression e) {
        // explicit convert
        if (e.arguments().size() != 1)
            return semantic("convert : %s", e.pos());
        var a = e.arguments().getFirst();
        var td = typeDeducer.visit(a);
        if (td instanceof PrimitiveTypeDeclarer atd) {
            if (ctd.primitive().isBool() == atd.primitive().isBool())
                return e;
        } else if (td instanceof LiteralTypeDeclarer lit) {
            if (ctd.primitive().isInteger() &&
                    lit.literal() instanceof IntegerLiteral)
                return e;

            if (ctd.primitive().isFloat() &&
                    lit.literal() instanceof FloatLiteral)
                return e;

            if (ctd.primitive().isBool() &&
                    lit.literal() instanceof BoolLiteral)
                return e;
        }

        return semantic("can't convert: %s", a.pos());
    }

    public Entity visit(CallExpression e) {
        visit(e.callee());
        e.arguments().forEach(this::visit);

        var td = typeDeducer.getCallable(e.callee());

        if (td instanceof FuncTypeDeclarer ftd) {
            if (!ftd.generic().isEmpty())
                return unsupported("generic");

            var left = ftd.prototype().parameterSet().types();
            var right = typeDeducer.visit(e.arguments());
            if (assignable(left, right, e.arguments())) return e;
            return semantic("arguments not compatible");
        }

        if (td instanceof ConvertorTypeDeclarer ctd) {
            return checkConvertor(ctd, e);
        }

        return unreachable();
    }

    public Entity visit(CurrentExpression e) {
        if (enterMethod != null)
            return e;
        return unreachable();
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
        arrayStack.push(e);
        visit(e.element());
        visit(e.length());
        var td = typeDeducer.visit(e.length());
        if ((td instanceof PrimitiveTypeDeclarer ptd &&
                ptd.primitive().isInteger()) ||
                (td instanceof LiteralTypeDeclarer ltd &&
                        ltd.literal() instanceof IntegerLiteral)) {
            arrayStack.pop();
            return e;
        }

        return semantic("array length must be integer: %s",
                e.length().pos());
    }

    public Entity visit(NewDerivedType e) {
        var def = visit(e.type());
        if (def instanceof ClassDefinition)
            return e;
        return semantic("require class: %s", e.type().pos());
    }

    private void checkMapArraySize(
            TypeDeclarer td, boolean flexible, boolean isTop) {
        if (td instanceof ArrayTypeDeclarer atd) {
            if (atd.length().none() && (!isTop || !flexible)) {
                semantic("require array length: %s",
                        td.pos());
                return;
            }

            if (atd.length().has()) {
                var len = atd.length().get();
                var lit = computeConstInteger(len);
                if (lit.value().compareTo(BigInteger.ZERO) < 0) {
                    semantic("array length must >0: %s", len.pos());
                    return;
                }
            }

            checkMapArraySize(atd.element(), flexible, false);
            return;
        }

        if (td instanceof DerivedTypeDeclarer dtd) {
            for (var atd : dtd.derivedType().generic().arguments()) {
                checkMapArraySize(atd, flexible, false);
            }
            return;
        }

        if (td instanceof PrimitiveTypeDeclarer) return;

        unreachable();
    }

    public Entity visit(NewMemType e) {
        if (e.mapped().none()) return e;
        if (checkMappable(e.mapped().get())) {
            checkMapArraySize(e.mapped().get(), false, true);
            return e;
        }
        return semantic("can't map type %s", e.pos());
    }

    public Entity visit(NewExpression e) {
        visit(e.type());
        e.arg().use(this::visit);
        if (e.arg().none()) return e;
        var arg = e.arg().get();

        if (e.type() instanceof NewMemType) {
            var right = typeDeducer.visit(arg);
            if (typeDeducer.isInteger(right)) return e;
            return semantic("ram length must integer: %s", arg.pos());
        }

        var argType = typeDeducer.visit(arg);

        if (e.type() instanceof NewDerivedType ndt) {
            if (argType instanceof DerivedTypeDeclarer dtd) {
                if (ndt.type().equals(dtd.derivedType()))
                    return e;
            }
            if (argType instanceof ObjectTypeDeclarer otd) {
                var etd = (DerivedTypeDeclarer) typeDeducer.visit(ndt);
                if (initializable(etd, otd))
                    return e;
            }
            return semantic("init '%s' not match new type %s: %s",
                    arg, e.type(), arg.pos());
        }

        if (!(argType instanceof ArrayTypeDeclarer atd)) {
            return semantic("init '%s' not match array type: %s",
                    arg, arg.pos());
        }
        var nat = (NewArrayType) e.type();
        if (assignable(nat.element(), atd.element(), Optional.of(arg)))
            return e;

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
