package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.PrimitiveType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.ast.var.*;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.layout.LayoutTool;
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.util.Stack;
import org.cossbow.feng.visit.SymbolContext;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.cossbow.feng.ast.dcl.ReferKind.PHANTOM;
import static org.cossbow.feng.ast.dcl.ReferKind.STRONG;
import static org.cossbow.feng.util.CommonUtil.subtract;
import static org.cossbow.feng.util.ErrorUtil.*;

public class SemanticAnalysis {

    private final StackedContext context;

    private final LiteralComputer computer = new LiteralComputer();
    private final ReturnAnalyzer analyzer = new ReturnAnalyzer();

    public SemanticAnalysis(SymbolContext parent) {
        context = new StackedContext(parent);
    }

    //

    private <Key> DAGGraph<Key> makeDAG(Collection<Key> nodes,
                                        Iterable<Groups.G2<Key, Key>> edges) {
        var dag = DAGGraph.make(nodes, edges);
        var c = dag.checkCyclic();
        if (c.isEmpty()) return dag;
        return semantic("cyclic dependence: %s", c);
    }

    //


    private List<EnumDefinition>
    visitEnum(IdentifierTable<TypeDefinition> types) {
        var enums = new ArrayList<EnumDefinition>();
        for (var t : types) {
            if (t instanceof EnumDefinition ed) {
                visit(ed);
                enums.add(ed);
            }
        }
        return enums;
    }

    private void visitFunc(IdentifierTable<TypeDefinition> types) {
        for (var t : types) {
            if (t instanceof PrototypeDefinition fd) visit(fd);
        }
    }

    private void visitAttribute(IdentifierTable<TypeDefinition> types) {
        for (var t : types) {
            if (t instanceof AttributeDefinition ad) visit(ad);
        }
    }

    private Set<GlobalVariable> searchDependencies(
            IdentifierTable<GlobalVariable> global,
            Expression expr, Set<Symbol> inited) {
        var deps = new HashSet<GlobalVariable>();
        var q = new ArrayDeque<Expression>();
        q.add(expr);
        while (!q.isEmpty()) {
            var c = q.poll();
            switch (c) {
                case ReferExpression e -> {
                    if (e.symbol().module().none()) {
                        var v = global.tryGet(e.symbol().name());
                        if (v.has()) {
                            deps.add(v.get());
                            break;
                        }
                    }
                    if (inited.contains(e.symbol())) {
                        break;
                    }
                    semantic("undeclared or unavailable: %s", e.symbol());
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


    private Optional<DAGGraph<GlobalVariable>>
    globalGraph(Collection<GlobalVariable> list,
                Set<Symbol> inited) {
        if (list.isEmpty()) return Optional.empty();

        var gvs = new IdentifierTable<GlobalVariable>(list.size());
        for (var gv : list) gvs.add(gv.name(), gv);

        var edges = new ArrayList<Groups.G2<GlobalVariable, GlobalVariable>>();
        for (var gv : list) {
            if (gv.init().none()) continue;
            var deps = searchDependencies(gvs, gv.init().must(), inited);
            for (var dep : deps) edges.add(Groups.g2(dep, gv));
        }
        var dag = makeDAG(gvs.values(), edges);
        return Optional.of(dag);
    }

    private void globalVarInferType(
            IdentifierTable<GlobalVariable> variables) {
        var dag = globalGraph(variables.values(), Set.of());
        if (dag.none()) return;
        dag.get().bfs(gv -> {
            if (gv.type().has()) {
                visit(gv.type().must());
                return;
            }
            var g = optimize(gv.init().must());
            gv.value().set(g.a());
            if (g.b() instanceof LiteralTypeDeclarer lit) {
                var t = lit.literal().compatible();
                if (t.none()) {
                    semantic("can't infer type: %s", gv.pos());
                    return;
                }
                gv.type().set(t.must().declarer(lit.pos()));
            } else {
                gv.type().set(g.b());
            }
        });
    }

    private Optional<DAGGraph<GlobalVariable>>
    globalVarInit(Collection<GlobalVariable> list,
                  Set<Symbol> inited) {
        var dag = globalGraph(list, inited);
        if (dag.none()) return dag;
        dag.get().bfs(gv -> visit(gv, gv.init().get()));
        return dag;
    }

    private boolean isConstPrimitive(Variable v) {
        return v.declare() == Declare.CONST &&
                v.type().match(t ->
                        t instanceof PrimitiveTypeDeclarer ||
                                t instanceof LiteralTypeDeclarer);
    }

    private Set<ArrayTypeDeclarer> valueArrayAll = new HashSet<>();
    private Set<ArrayTypeDeclarer> valueArrays = new LinkedHashSet<>();

    private void collectArray(Definition def) {
        def.arrays(List.copyOf(valueArrays));
        valueArrays.clear();
    }

    public Entity visit(Source s) {
        var tab = s.table();

        globalVarInferType(tab.variables);
        var constValues = tab.variables
                .stream().filter(this::isConstPrimitive)
                .collect(Collectors.toSet());
        tab.dagConst = globalVarInit(constValues, Set.of());

        tab.enumList = visitEnum(tab.namedTypes);
        tab.dagStructures = visitStructures(tab.namedTypes);
        tab.dagInterfaces = visitInterfaces(tab.namedTypes);
        tab.dagClasses = visitClasses(tab.namedTypes);

        var rest = subtract(tab.variables.values(), constValues);
        tab.dagVars = globalVarInit(rest, constValues.stream()
                .map(GlobalVariable::symbol)
                .collect(Collectors.toSet()));

        visitFunc(tab.namedTypes);
        tab.dagClasses.use(this::visitMethods);
        visitAttribute(tab.namedTypes);

        for (var f : tab.namedFunctions) visit(f);

        return s;
    }

    public Entity visit(Modifier m) {
        if (!m.attributes().isEmpty())
            unsupported("attribute");
        return m;
    }

    public Entity visit(Refer e) {
        return e;
    }

    public Entity visit(TypeArguments ta) {
        if (ta.isEmpty()) return ta;
        return unsupported("generic");
    }

    public TypeDefinition visit(DerivedType dt) {
        visit(dt.generic());
        var type = context.findType(dt.symbol());
        if (type.none())
            return semantic("type %s not defined: %s",
                    dt.symbol(), dt.pos());
        visit(type.get().generic());
        return type.get();
    }

    private TypeDefinition findDef(DerivedTypeDeclarer dtd) {
        return visit(dtd.derivedType());
    }

    //

    private boolean enablePhantom = false;

    public Entity visit(TypeDeclarer td) {
        if (enablePhantom) {
            enablePhantom = false;
        } else {
            illegalPhantom(td);
        }
        td.mappable(mappable(td));
        return switch (td) {
            case ArrayTypeDeclarer ee -> visit(ee);
            case DerivedTypeDeclarer ee -> visit(ee);
            case FuncTypeDeclarer ee -> visit(ee);
            case PrimitiveTypeDeclarer ee -> visit(ee);
            case LiteralTypeDeclarer ee -> visit(ee);
            case ObjectTypeDeclarer ee -> visit(ee);
            case null, default -> unreachable();
        };
    }

    public TypeDefinition visit(DerivedTypeDeclarer td) {
        var dt = visit(td.derivedType());
        td.def.set(dt);
        var r = td.refer();
        if (r.has()) visit(r.get());

        if (dt instanceof StructureDefinition) return dt;

        if (dt instanceof ClassDefinition cd) {
            if (cd.resource() && r.none()) {
                return semantic("resource-class %s must be refer: %s",
                        cd.symbol(), td.pos());
            }
            return dt;
        }

        if (dt instanceof InterfaceDefinition) {
            if (r.has()) return dt;
            return semantic("interface must be refer: %s",
                    td.pos());
        }

        if (r.none()) return dt;
        return semantic("can't refer %s %s", dt.domain(), dt.symbol());
    }

    private void illegalPhantom(TypeDeclarer td) {
        var ref = td.maybeRefer();
        if (ref.match(r -> r.isKind(PHANTOM))) {
            semantic("can't be phantom-reference: %s",
                    td.pos());
        }
    }

    public Entity visit(ArrayTypeDeclarer td) {
        visit(td.element());
        if (td.length().has()) {
            var l = td.length().get();
            var s = calcSize(l);
            td.length(Optional.of(new LiteralExpression(l.pos(), s)));
            td.len(s.value().longValue());
        } else {
            visit(td.refer().must());
        }
        // collect value array
        if (td.refer().none() && valueArrayAll.add(td))
            valueArrays.add(td);
        return td;
    }

    public Entity visit(FuncTypeDeclarer td) {
        visit(td.prototype(), false);
        visit(td.generic());
        return td;
    }

    public Entity visit(PrimitiveTypeDeclarer ptd) {
        return ptd;
    }

    public Entity visit(LiteralTypeDeclarer ltd) {
        return ltd;
    }

    public Entity visit(ObjectTypeDeclarer otd) {
        return otd;
    }

    public Entity visit(TypeParameters e) {
        if (e.isEmpty()) return e;
        return unsupported("generic");
    }

    // structure define

    private Stream<TypeDeclarer> structureDepSizeof(Expression root) {
        var list = new ArrayList<TypeDeclarer>();
        var q = new ArrayDeque<Expression>();
        q.add(root);
        while (!q.isEmpty()) {
            var c = q.poll();
            switch (c) {
                case SizeofExpression e -> list.add(e.type());
                case BinaryExpression e -> {
                    q.add(e.left());
                    q.add(e.right());
                }
                case UnaryExpression e -> q.add(e.operand());
                case ParenExpression e -> q.add(e.child());
                case ReferExpression e -> {
                }
                case LiteralExpression e -> {
                }
                default -> semantic("can't use %s: %s", c, c.pos());
            }
        }
        return list.stream();
    }

    private Stream<StructureDefinition> structureInitDeps(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            if (!ptd.primitive().isBool())
                return Stream.empty();
            return semantic("require integer or float: %s", ptd.pos());
        }

        if (td instanceof DerivedTypeDeclarer dtd) {
            if (dtd.refer().has())
                return semantic("can't be reference");

            if (!dtd.derivedType().generic().isEmpty())
                return unsupported("generic");

            var def = visit(dtd.derivedType());
            dtd.def.set(def);
            if (def instanceof StructureDefinition sd)
                return Stream.of(sd);

            return semantic("structure field type can't be %s: %s",
                    def, td.pos());
        }

        if (td instanceof ArrayTypeDeclarer atd) {
            if (atd.refer().has())
                return semantic("can't be reference");

            var l = atd.length().must();
            var sizeof = structureDepSizeof(l);
            var element = Stream.of(atd.element());
            return Stream.concat(element, sizeof).flatMap(this::structureInitDeps);
        }

        return semantic("illegal type: %s%s", td, td.pos());
    }

    public Optional<DAGGraph<StructureDefinition>>
    visitStructures(IdentifierTable<TypeDefinition> types) {
        var all = new ArrayList<StructureDefinition>(types.size());
        var edges = new ArrayList<Groups.G2<StructureDefinition, StructureDefinition>>();
        for (var def : types) {
            if (def.builtin()) continue;
            if (!(def instanceof StructureDefinition sd))
                continue;
            var a = sd.fields().stream()
                    .map(StructureField::type);
            var b = sd.fields().stream()
                    .flatMap(sf -> sf.bitfield().stream())
                    .flatMap(this::structureDepSizeof);
            Stream.concat(a, b).flatMap(this::structureInitDeps)
                    .forEach(d -> edges.add(Groups.g2(d, sd)));
            all.add(sd);
        }
        if (all.isEmpty()) return Optional.empty();
        var dag = makeDAG(all, edges);
        var layoutTool = new LayoutTool(context);
        dag.bfs(sd -> {
            if (sd.builtin()) return;
            visitStructure(sd);
            var layout = layoutTool.buildLayout(sd);
            sd.layout().set(layout);
        });
        return Optional.of(dag);
    }

    private void visitStructure(StructureDefinition sd) {
        for (var sf : sd.fields()) {
            visitBitfield(sf);
            visit(sf.type());
            collectArray(sd);
        }
    }

    private void visitBitfield(StructureField sf) {
        if (sf.bitfield().none()) return;

        var bf = sf.bitfield().get();
        if (!(sf.type() instanceof PrimitiveTypeDeclarer ptd
                && ptd.primitive().isInteger())) {
            semantic("bitfield must be integer: %s", bf.pos());
            return;
        }

        var v = calcSize(bf).value().intValue();
        if (v > 0 && v <= ptd.primitive().width) {
            sf.bits(v);
            return;
        }

        semantic("field width must in range [1,%s]: %s",
                ptd.primitive().width, bf.pos());
    }


    // enum

    public Entity visit(EnumDefinition def) {
        var i = 0;
        for (var v : def.values()) {
            v.val(i++);
            if (v.init().none()) {
                continue;
            }
            var il = calcInteger(v.init().must());
            v.init().set(new LiteralExpression(il.pos(), il));
            v.val(il.value().intValue());
        }

        return def;
    }

    //

    private static Optional<Primitive>
    primitiveKind(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd)
            return Optional.of(ptd.primitive());
        if (td instanceof LiteralTypeDeclarer ltd)
            return ltd.literal().compatible();
        return Optional.empty();
    }

    private static boolean isInteger(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd)
            return ptd.primitive().isInteger();

        if (td instanceof LiteralTypeDeclarer ltd)
            return ltd.literal() instanceof IntegerLiteral;

        return false;
    }

    //

    private int checkCompatible(Prototype l, Prototype r) {
        if (!l.parameterSet().equals(r.parameterSet()))
            return 1;
        var lr = l.returnSet();
        var rr = r.returnSet();
        if (lr.none() != rr.none())
            return 2;
        if (lr.none()) return 0;
        if (!assignable(lr.get(), rr.get(), Optional.empty(), true))
            return 2;
        return 0;
    }

    private boolean compatible(Prototype l, Prototype r) {
        var re = checkCompatible(l, r);
        return switch (re) {
            case 1 -> semantic("parameters not same: %s", r.pos());
            case 2 -> semantic("returns not compatible: %s", r.pos());
            default -> true;
        };
    }

    // class define

    private void checkInherit(
            ClassDefinition parent, ClassDefinition child,
            IdentifierTable<ClassField> allFields,
            IdentifierTable<ClassMethod> allMethods) {
        // 检查同名属性
        for (var pf : parent.allFields()) {
            var cf = child.fields().tryGet(pf.name());
            if (cf.none()) {
                allFields.add(pf.name(), pf);
                continue;
            }
            semantic("can't hide parent field: %s%s",
                    pf.name(), cf.get().pos());
            return;
        }

        // 检查方法覆盖是否兼容
        for (var pm : parent.allMethods()) {
            var o = child.methods().tryGet(pm.name());
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
            compatible(pp, cp);
            pm.override().add(cm);
        }

    }

    private ClassDefinition findClass(DerivedType t) {
        var dt = visit(t);
        if (dt instanceof ClassDefinition pcd)
            return pcd;
        return semantic("require class: %s", dt.symbol());
    }

    private void checkResource(ClassDefinition cd) {
        var res = cd.allMethods().tryGet(ClassDefinition.ReleaseName);
        if (res.none()) return;
        var m = res.get();
        if (!m.prototype().parameterSet().isEmpty()) {
            semantic("release method can't has parameter");
        }
        if (m.prototype().returnSet().has()) {
            semantic("release method can't has return");
        }
        cd.resource(true);
    }

    private void checkAllInherits(ClassDefinition cd) {
        if (cd.builtin()) return;
        var allFields = new IdentifierTable<>(cd.fields().nodes());
        var allMethods = new IdentifierTable<>(cd.methods().nodes());
        if (cd.parent().has()) {
            checkInherit(cd.parent().must(), cd, allFields, allMethods);
        }
        for (var c = cd.parent(); c.has(); c = c.must().parent()) {
            cd.ancestors().add(c.must());
        }
        cd.allFields().addAll(allFields);
        cd.allMethods().addAll(allMethods);
        checkResource(cd);
    }

    private void checkImplList(InterfaceDefinition id, ClassDefinition cd) {
        for (var im : id.allMethods) {
            var o = cd.allMethods().tryGet(im.name());
            if (o.none()) {
                semantic("%s unimplement method: %s%s",
                        cd.symbol(), im.name(), im.pos());
                return;
            }
            var cm = o.must();
            compatible(im.prototype(), cm.prototype());
            im.override().add(cm);
        }
    }

    private void checkImplList(ClassDefinition cd) {
        cd.parent().use(pd ->
                cd.allImpls().addAll(pd.allImpls()));
        for (var dt : cd.impl()) {
            var td = visit(dt);
            if ((td instanceof InterfaceDefinition id)) {
                checkImplList(id, cd);
                cd.allImpls().add(id);
                id.visitParts(d -> cd.allImpls().add(d));
                continue;
            }
            semantic("require interface: %s", dt.pos());
        }
    }

    private Optional<ClassDefinition>
    getClassTypeField(TypeDeclarer t) {
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

    private List<ClassDefinition> findInitDeps(ClassDefinition cd) {
        var inherit = cd.inherit().stream().map(this::findClass)
                .peek(p -> cd.parent().setIfNone(p));
        var fields = cd.fields().stream().map(ClassField::type)
                .map(this::getClassTypeField).flatMap(Optional::stream);
        return Stream.concat(inherit, fields).toList();
    }

    private Optional<DAGGraph<ClassDefinition>>
    visitClasses(IdentifierTable<TypeDefinition> types) {
        var all = new ArrayList<ClassDefinition>(types.size());
        var edges = new ArrayList<Groups.G2<ClassDefinition, ClassDefinition>>();
        for (var t : types) {
            if (!(t instanceof ClassDefinition cd)) continue;
            var deps = findInitDeps(cd);
            for (var dep : deps) edges.add(Groups.g2(dep, cd));
            all.add(cd);
        }
        if (all.isEmpty()) return Optional.empty();
        var dag = makeDAG(all, edges);
        dag.bfs(this::checkAllInherits);
        dag.bfs(this::checkImplList);
        dag.bfs(this::visitClass);
        dag.bfs(cd -> new UpdaterAnalyzer().analyse(cd));
        return Optional.of(dag);
    }

    private void visitMethods(DAGGraph<ClassDefinition> dag) {
        dag.bfs(this::visitMethod);
    }

    private ClassDefinition enterClass;
    private ClassMethod enterMethod;

    private void visitClass(ClassDefinition cd) {
        if (cd.builtin()) return;

        assert enterClass == null;
        enterClass = cd;

        if (!cd.generic().isEmpty()) {
            unsupported("generic");
            return;
        }

        for (var f : cd.fields()) visit(f);

        collectArray(cd);
        enterClass = null;
    }

    public Entity visit(ClassField cf) {
        assert enterClass != null;
        visit(cf.type());
        return cf;
    }

    public void visitMethod(ClassDefinition cd) {
        assert enterClass == null;
        enterClass = cd;

        for (var m : cd.methods()) visit(m);

        if (!cd.macros().isEmpty())
            unsupported("macro");

        enterClass = null;
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

    private CurrentExpression newThis(Position pos) {
        var ce = new CurrentExpression(pos, enterClass.symbol(),
                enterMethod.name(), true);
        var g = optimize(ce);
        ce.resultType.set(g.b());
        return ce;
    }

    private MemberOfExpression wrapThis(Symbol s, Field f) {
        assert s.module().none();
        return new MemberOfExpression(s.pos(), newThis(s.pos()),
                s.name(), TypeArguments.EMPTY, f);
    }

    private MemberOfExpression wrapThis(ReferExpression e, ClassMethod m) {
        assert e.symbol().module().none();
        return new MemberOfExpression(e.pos(), newThis(e.pos()),
                e.symbol().name(), e.generic(), m);
    }

    private List<InterfaceDefinition>
    findParts(InterfaceDefinition def) {
        return def.parts().stream().map(p -> {
            var t = visit(p);
            if (t instanceof InterfaceDefinition id) return id;
            return semantic("component must be interface: %s", p.pos());
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

    private Optional<DAGGraph<InterfaceDefinition>>
    visitInterfaces(IdentifierTable<TypeDefinition> types) {
        var all = new ArrayList<InterfaceDefinition>(types.size());
        var edges = new ArrayList<Groups.G2<InterfaceDefinition, InterfaceDefinition>>();
        for (var t : types) {
            if (!(t instanceof InterfaceDefinition id)) continue;

            var parts = findParts(id);
            for (var part : parts) {
                edges.add(Groups.g2(part, id));
            }
            id.partDefs.addAll(parts);
            all.add(id);
        }
        if (all.isEmpty()) return Optional.empty();
        var dag = makeDAG(all, edges);
        dag.bfs(this::visit);
        return Optional.of(dag);
    }

    public Entity visit(InterfaceDefinition def) {
        if (def.builtin()) return def;
        if (!def.generic().isEmpty()) return unsupported("generic");

        for (var m : def.methods()) visit(m);

        var all = new HashMap<Identifier, InterfaceMethod>();
        for (var m : def.methods()) all.put(m.name(), m);
        for (var dep : def.partDefs) {
            var c = compatible(dep, all);
            c.use(g -> {
                semantic("duplicate method %s <--> %s",
                        g.a().pos(), g.b().pos());
            });
        }
        all.forEach(def.allMethods::add);

        collectArray(def);
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
        collectArray(pd);
        return pd;
    }

    //

    public Entity visit(FunctionDefinition fd) {
        visit(fd.generic());
        visit(fd.modifier());
        visit(fd.procedure());
        collectArray(fd);
        return fd;
    }

    private Procedure enterProc;

    public Entity visit(Procedure proc) {
        assert enterProc == null;
        enterProc = proc;
        context.enterScope();
        visit(proc.prototype(), true);
        visit(proc.body());
        checkAllPathReturn(proc);
        context.exitScope(proc);
        enterProc = null;
        return proc;
    }

    private void checkAllPathReturn(Procedure proc) {
        if (analyzer.check(proc.body()))
            return;
        if (proc.prototype().returnSet().none()) return;

        semantic("missing return statement: %s",
                enterProc.pos());
    }

    public Entity visit(Prototype prot, boolean addVar) {
        visit(prot.parameterSet(), addVar);
        prot.returnSet().use(this::visit);
        return prot;
    }

    private void visit(ParameterSet ps, boolean addVar) {
        for (var v : ps.variables()) {
            visit(v.modifier());
            enablePhantom = true;
            visit(v.type().must());
            if (addVar) context.putVar(v);
        }
    }

    //

    private boolean enablePhantom(Expression e) {
        return switch (e) {
            case IndexOfExpression ee -> enablePhantom(ee);
            case LiteralExpression ee -> enablePhantom(ee);
            case MemberOfExpression ee -> enablePhantom(ee);
            case ParenExpression ee -> enablePhantom(ee.child());
            case ReferExpression ee -> enablePhantom(ee);
            case VariableExpression ee -> enablePhantom(ee.variable());
            case AssertExpression ee -> false;
            case SizeofExpression ee -> false;
            case NewExpression ee -> false;
            case CurrentExpression ee -> true;
            default -> semantic("can't refer: %s", e.pos());
        };
    }

    private boolean enablePhantom(Variable v) {
        var isRefer = v.type().must().maybeRefer().has();
        return isRefer || v.declare() != Declare.CONST;
    }

    private boolean enablePhantom(ReferExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");
        var v = context.findVar(e.symbol());
        if (v.none()) return semantic("not declared: %s", e.pos());

        return enablePhantom(v.get());
    }

    private boolean enablePhantom(IndexOfExpression e) {
        var g = optimize(e.subject());
        if (!(g.b() instanceof ArrayTypeDeclarer atd))
            return unreachable();
        return !atd.refer().has() && enablePhantom(e.subject());
    }

    private boolean enablePhantom(MemberOfExpression e) {
        if (e.field().none()) {
            return semantic("operable field not found %s: %s",
                    e.member(), e.member().pos());
        }
        var f = e.field().get();
        var able = f.type().maybeRefer().has() == f.immutable();
        return enablePhantom(e.subject()) == able;
    }

    private boolean enablePhantom(LiteralExpression e) {
        return e.literal() instanceof StringLiteral
                || e.literal() instanceof NilLiteral;
    }

    private boolean enablePhantom(Optional<Expression> re) {
        if (re.none()) return true;
        if (enablePhantom(re.get())) {
            if (re.get() instanceof VariableExpression ve) {
                context.lockVar(ve.variable());
            }
            return true;
        }
        return semantic("can't use phantom reference: %s", re.get().pos());
    }

    //

    private final Stack<Statement> stmtStack = new Stack<>();

    public Statement visit(Statement s) {
        stmtStack.push(s);
        s = switch (s) {
            case AssignmentsStatement ss -> visit(ss);
            case BlockStatement ss -> visit(ss);
            case BreakStatement ss -> visit(ss);
            case CallStatement ss -> visit(ss);
            case ContinueStatement ss -> visit(ss);
            case DeclarationStatement ss -> visit(ss);
            case ForStatement ss -> visit(ss);
            case GotoStatement ss -> visit(ss);
            case IfStatement ss -> visit(ss);
            case LabeledStatement ss -> visit(ss);
            case ReturnStatement ss -> visit(ss);
            case SwitchStatement ss -> visit(ss);
            case ThrowStatement ss -> visit(ss);
            case TryStatement ss -> visit(ss);
            case SwitchBranch ss -> visit(ss);
            case CatchClause ss -> visit(ss);
            case null, default -> unreachable();
        };
        stmtStack.pop();
        return s;
    }

    private <S extends Statement> void visit(List<S> list) {
        for (var s : list) visit(s);
    }

    private <S extends Statement>
    Optional<Statement> visit(Optional<S> so) {
        return so.map(this::visit);
    }

    public Statement visit(BlockStatement bs) {
        if (bs.newScope()) context.enterScope();
        for (var s : bs.list())
            visit(s);
        if (bs.newScope()) context.exitScope(bs);
        return bs;
    }

    private <F extends Field> List<Field> checkInitField(
            IdentifierTable<F> fields,
            ObjectTypeDeclarer odt, ObjectExpression oe) {
        var keys = new ArrayList<Field>();
        for (var f : fields) {
            var o = oe.entries().tryGet(f.name());
            if (o.none()) {
                if (!f.immutable()) continue;
                return semantic("const field must init: %s",
                        oe.pos());
            }

            var v = o.get();
            var t = odt.entries().get(f.name());
            var able = assignable(f.type(), t, o);
            if (!able) return semantic(
                    "incompatible field '%s' and value '%s': %s",
                    f.name(), v, v.pos());
            keys.add(f);
        }
        return keys;
    }

    private boolean initializable(
            StructureDefinition sd,
            ObjectTypeDeclarer odt, ObjectExpression oe) {
        var keys = checkInitField(sd.fields(), odt, oe);
        oe.initStack.add(keys);
        return true;
    }

    private boolean initializable(
            ClassDefinition cd,
            ObjectTypeDeclarer odt, ObjectExpression oe) {
        for (var k : oe.entries().keys()) {
            if (cd.allFields().exists(k)) continue;
            return semantic("unknown field '%s': %s", k, k.pos());
        }

        var initKeys = new HashSet<>(oe.entries().keys());
        var def = cd;
        while (true) {
            var fields = checkInitField(def.fields(), odt, oe);
            oe.initStack.add(fields);
            for (var f : fields) initKeys.remove(f.name());
            if (initKeys.isEmpty()) break;
            if (def.parent().none()) break;
            def = def.parent().must();
            if (def == ClassDefinition.ObjectClass) break;
        }

        return true;
    }

    private boolean initializable(
            DerivedTypeDeclarer l,
            ObjectTypeDeclarer o, ObjectExpression oe) {
        if (oe.entries().isEmpty()) return true;

        var lt = visit(l.derivedType());

        if (lt instanceof StructureDefinition def)
            return initializable(def, o, oe);

        if (lt instanceof ClassDefinition def)
            return initializable(def, o, oe);

        return false;
    }

    private boolean assignable(ClassDefinition l, ClassDefinition r) {
        visit(l.generic());
        visit(r.generic());

        if (r.equals(l)) return true;
        return r.parent().match(p -> assignable(l, p));
    }

    private boolean assignable(InterfaceDefinition l, ClassDefinition r) {
        visit(l.generic());
        visit(r.generic());

        if (r.impl().exists(l.symbol())) return true;

        var ok = r.parent().match(p -> assignable(l, p));
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

    private boolean assignable(
            ObjectDefinition l, ObjectDefinition r,
            boolean covariant) {
        if (!covariant) return l.equals(r);

        if (l instanceof ClassDefinition lc) {
            if (l == ClassDefinition.ObjectClass) {
                // class Object is root of all objects
                return true;
            }
            if (r instanceof ClassDefinition rc) {
                return assignable(lc, rc);
            }
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
               boolean covariant) {
        visit(l.derivedType().generic());
        visit(r.derivedType().generic());

        var lt = visit(l.derivedType());
        var rt = visit(r.derivedType());
        if (lt instanceof ObjectDefinition lo &&
                rt instanceof ObjectDefinition ro)
            return assignable(lo, ro, covariant);
        return false;
    }

    private boolean compatible(
            DerivedTypeDeclarer l, FuncTypeDeclarer r) {
        visit(r.generic());
        var ldt = visit(l.derivedType());
        return ldt instanceof PrototypeDefinition lpd &&
                compatible(lpd.prototype(), r.prototype());
    }

    private boolean compatible(
            FuncTypeDeclarer l, DerivedTypeDeclarer r) {
        visit(l.generic());
        var rt = visit(r.derivedType());
        return rt instanceof PrototypeDefinition rd &&
                compatible(l.prototype(), rd.prototype());
    }

    private boolean referable(Refer lr, Optional<Refer> r,
                              Optional<Expression> re) {
        if (lr.kind() == PHANTOM) {
            return enablePhantom(re);
        }
        assert lr.kind() == STRONG;

        if (!r.has()) {
            return semantic("strong-refer can't refer value: %s", lr.pos());
        }
        if (r.get().isKind(PHANTOM)) {
            return semantic("phantom-reference can't deliver to strong-reference: %s", lr.pos());
        }

        return true;
    }

    private boolean assignRefer(TypeDeclarer l, LiteralTypeDeclarer rt) {
        assert l.maybeRefer().has();
        if (rt.isNil()) return true;
        if (rt.isString()) {
            if (!(l instanceof ArrayTypeDeclarer la))
                return false;

            if (!(la.element() instanceof PrimitiveTypeDeclarer lp))
                return false;

            return lp.primitive() == Primitive.UINT8 &&
                    la.refer().get().isKind(STRONG);
        }
        return false;
    }

    private boolean mappable(TypeDeclarer td, boolean mustValue) {
        if (mustValue && td.maybeRefer().has()) {
            return false;
        }
        if (td instanceof PrimitiveTypeDeclarer ptd)
            return !ptd.primitive().isBool();
        if (td instanceof DerivedTypeDeclarer dtd) {
            var type = findDef(dtd);
            return type instanceof StructureDefinition;
        }
        if (td instanceof ArrayTypeDeclarer atd) {
            return mappable(atd.element(), true);
        }
        return false;
    }

    private boolean mappable(TypeDeclarer td) {
        return mappable(td, false);
    }

    private long typeSize(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd)
            return ptd.primitive().size();

        if (td instanceof DerivedTypeDeclarer dtd) {
            var type = visit(dtd.derivedType());
            if (type instanceof StructureDefinition sd)
                return sd.layout().must().size();
        }

        return semantic("unsupported sizeof(%s): %s", td, td.pos());
    }

    public long estimateSize(TypeDeclarer td) {
        if (!(td instanceof ArrayTypeDeclarer atd))
            return typeSize(td);

        if (atd.refer().has())
            return semantic("can't get size of %s: %s", td, td.pos());

        var es = estimateSize(atd.element());
        return atd.len() * es;
    }

    // convertible types: struct/union/primitive/[]struct/[]union/[]primitive
    private boolean checkBounds(TypeDeclarer l, TypeDeclarer r) {
        assert l.maybeRefer().has();
        if (r instanceof ArrayTypeDeclarer ra) {
            if (ra.refer().has()) {
                ra.unit(estimateSize(ra.element()));
                if (l instanceof ArrayTypeDeclarer la) {
                    // [*]E = [*]S
                    // [&]E = [*]S
                    la.unit(estimateSize(la.element()));
                } else {
                    // *E = [*]S
                    // &E = [*]S
                    l.size(typeSize(l));
                }
                return true;
            } else {
                r.size(estimateSize(r));
                if (l instanceof ArrayTypeDeclarer la) {
                    // [&]E = [N]S
                    la.unit(estimateSize(la.element()));
                    return true;
                } else {
                    // &E = [N]S
                    l.size(typeSize(l));
                    return l.size() <= r.size();
                }
            }
        } else {
            r.size(typeSize(r));
            if (l instanceof ArrayTypeDeclarer la) {
                // [*]E = *S
                // [&]E = *S
                // [&]E = S
                la.unit(estimateSize(la.element()));
                return true;
            } else {
                // *E = *S
                // &E = *S
                // &E = S
                l.size(typeSize(l));
                return l.size() <= r.size();
            }
        }
    }

    private boolean assignRefer(TypeDeclarer l, TypeDeclarer r,
                                Optional<Expression> re, boolean covariant) {
        var lRef = l.maybeRefer();
        assert lRef.has();

        if (r instanceof LiteralTypeDeclarer rt)
            return assignRefer(l, rt);

        // throw out exception
        if (!referable(lRef.get(), r.maybeRefer(), re))
            return false;

        if (mappable(l) && mappable(r)) {
            if (checkBounds(l, r))
                return true;

            return semantic("out of bounds: convert %s(size=%d):%s to %s(size=%d):%s",
                    l, l.size(), l.pos(), r, r.size(), r.pos());
        }

        // Objects
        if (r instanceof DerivedTypeDeclarer rd) {
            if (l instanceof DerivedTypeDeclarer ld)
                return assignable(ld, rd, covariant);
        }

        // Arrays
        if (r instanceof ArrayTypeDeclarer ra) {
            if (l instanceof ArrayTypeDeclarer la) {
                return assignable(la.element(), ra.element(),
                        re, false);
            }
        }

        return semantic("%s can't refer %s: %s", l, r, l.pos());
    }

    private boolean assignValue(TypeDeclarer l, LiteralTypeDeclarer rt) {
        assert l.maybeRefer().none();

        if (rt.isInteger())
            return l instanceof PrimitiveTypeDeclarer ptd
                    && ptd.primitive().isInteger();

        if (rt.isFloat())
            return l instanceof PrimitiveTypeDeclarer ptd
                    && ptd.primitive().isFloat();

        if (rt.isBool())
            return l instanceof PrimitiveTypeDeclarer ptd
                    && ptd.primitive().isBool();

        return false;
    }

    private boolean assignValue(
            ArrayTypeDeclarer l, ArrayTypeDeclarer r,
            Optional<Expression> re) {
        assert l.refer().none();

        if (r.refer().has()) return false; // Not: [value] = [refer]

        if (re.has()) {
            if (re.must() instanceof ArrayExpression ae) {
                var i = 0;
                for (var ev : ae.elements()) {
                    if (assignable(l.element(), r.element(),
                            Optional.of(ev), true)) {
                        i++;
                        continue;
                    }
                    return semantic(
                            "array element at %d not match type %s: %s",
                            i, l.element(), ev.pos());
                }
            } else {
                if (!assignable(l.element(), r.element(),
                        Optional.empty(), false))
                    return false;
            }
        } else {
            var elOk = assignable(l.element(), r.element(),
                    Optional.empty(), false);
            if (!elOk) return false;
        }

        if (re.match(e -> e instanceof ArrayExpression)) {
            if (r.element() instanceof VoidTypeDeclarer) return true;
            if (l.len() < r.len()) {
                return semantic("index out of bound: %s", r.pos());
            }
            return true;
        }
        return l.len().longValue() == r.len().longValue();
    }

    private boolean assignValue(
            TypeDeclarer l, TypeDeclarer r,
            Optional<Expression> re) {
        assert l.maybeRefer().none();
        // primitive check by .equals();

        if (r instanceof LiteralTypeDeclarer rt)
            return assignValue(l, rt);

        if (r instanceof ObjectTypeDeclarer ro) {
            if (l instanceof DerivedTypeDeclarer ld)
                return initializable(ld, ro,
                        (ObjectExpression) re.must());
        }

        if (r instanceof ArrayTypeDeclarer ra) {
            if (l instanceof ArrayTypeDeclarer la) {
                return assignValue(la, ra, re);
            }
        }

        if (r instanceof FuncTypeDeclarer rf) {
            if (l instanceof DerivedTypeDeclarer ld)
                return compatible(ld, rf);
            if (l instanceof FuncTypeDeclarer lf)
                return compatible(lf.prototype(),
                        rf.prototype());
        }

        if (r instanceof DerivedTypeDeclarer rd) {
            if (l instanceof DerivedTypeDeclarer ld) {
                return ld.derivedType().equals(rd.derivedType()) &&
                        (rd.refer().none() || re.match(e ->
                                e instanceof CurrentExpression));
            }
            if (l instanceof FuncTypeDeclarer lf)
                return compatible(lf, rd);
        }

        if (r instanceof EnumTypeDeclarer rd) {
            if (l instanceof DerivedTypeDeclarer ld) {
                var def = visit(ld.derivedType());
                return def.equals(rd.def());
            }
        }

        return false;
    }

    private boolean assignable(
            TypeDeclarer l, TypeDeclarer r,
            Optional<Expression> re, boolean covariant) {
        Objects.requireNonNull(l, "left");
        Objects.requireNonNull(r, "right");

        if (l.equals(r)) return true;

        var lr = l.maybeRefer();
        if (lr.none()) return assignValue(l, r, re);

        return assignRefer(l, r, re, covariant);
    }

    private boolean assignable(
            TypeDeclarer l, TypeDeclarer r,
            Optional<Expression> re) {
        return assignable(l, r, re, true);
    }


    private boolean assignable(List<TypeDeclarer> left,
                               List<TypeDeclarer> right,
                               List<Expression> es) {
        if (left.size() != right.size() || (!es.isEmpty() && es.size() != left.size()))
            return semantic("size not match: %s", left.getFirst().pos());

        for (int i = 0; i < left.size(); i++) {
            var l = left.get(i);
            var r = right.get(i);
            var e = es.isEmpty() ? null : es.get(i);
            if (!assignable(l, r, Optional.of(e)))
                return semantic("incompatible %s = %s: %s", l, r, l.pos());
        }

        return true;
    }

    private Assignment visit(Assignment a) {
        var og = optimize(a.operand());
        var vg = optimize(a.value());
        if (assignable(og.b(), vg.b(), Optional.of(vg.a()))) {
            return new Assignment(a.pos(), og.a(), vg.a(), a.copy());
        }
        return semantic("incompatible %s = %s: %s",
                a.operand(), a.value(), a.pos());
    }

    public Statement visit(AssignmentsStatement as) {
        var list = as.list().stream().map(this::visit).toList();
        return new AssignmentsStatement(as.pos(), list);
    }

    private void checkDefinedType(TypeDeclarer td) {
        if (td instanceof ArrayTypeDeclarer atd)
            td = atd.element();
        if (td instanceof ObjectTypeDeclarer ||
                td instanceof VoidTypeDeclarer)
            semantic("can't deduce type: %s", td.pos());
    }

    private void initVar(Variable var, Expression e) {
        var g = optimize(e);
        var.value().set(g.a());
        var t = g.b();
        if (var.type().has()) {
            var l = var.type().must();
            // check type
            if (assignable(l, t, Optional.of(g.a()))) return;
            semantic("incompatible, %s:%s can't assign %s:%s",
                    l, l.pos(), t, t.pos());
        } else {
            checkDefinedType(t);
            // auto set type
            if (t instanceof LiteralTypeDeclarer ltd) {
                var p = ltd.literal().compatible();
                if (p.has()) t = p.get().declarer(t.pos());
            }
            var.type().set(t);
        }
    }

    private void visit(Variable v, Optional<Expression> init) {
        visit(v.modifier());
        v.type().use(t -> {
            enablePhantom = true;
            visit(t);
        });
        if (init.none()) {
            if (v.declare() == Declare.CONST) {
                semantic("const must init: %s", v.pos());
                return;
            }
            var v0 = defaultValue(v.type().must(), v);
            v.defVal().set(v0);
        } else {
            initVar(v, init.must());
        }
    }

    public Statement visit(DeclarationStatement ds) {
        var i = 0;
        for (var v : ds.variables()) {
            var init = ds.init().isEmpty() ?
                    Optional.<Expression>empty() :
                    Optional.of(ds.init().get(i));
            visit(v, init);
            context.putVar(v);
            i++;
        }
        return ds;
    }

    public Statement visit(CallStatement e) {
        var g = optimize(e.call());
        e.call((CallExpression) g.a());
        return e;
    }

    public Statement visit(LabeledStatement e) {
        return visit(e.target());
    }

    private final Stack<ForStatement> loopStack = new Stack<>();

    private void checkLabel(Identifier label) {
        assert enterProc != null;
        if (enterProc.labels().contains(label)) return;
        semantic("label not found: %s", label.pos());
    }

    public Statement visit(BreakStatement e) {
        e.label().use(this::checkLabel);

        if (loopStack.isEmpty())
            return semantic("out of loop: %s", e.pos());

        return e;
    }

    public Statement visit(ContinueStatement e) {
        e.label().use(this::checkLabel);

        if (loopStack.isEmpty())
            return semantic("out of loop: %s", e.pos());

        return e;
    }

    public Statement visit(ForStatement e) {
        loopStack.push(e);
        context.enterScope();
        e = switch (e) {
            case ConditionalForStatement ee -> visit(ee);
            case IterableForStatement ee -> visit(ee);
            case null, default -> unreachable();
        };
        visit(e.body());
        context.exitScope(e);
        loopStack.pop();
        return e;
    }

    public ForStatement visit(ConditionalForStatement e) {
        visit(e.initializer());
        var cg = optimize(e.condition());
        e.condition(cg.a());
        if (!cg.b().isBool())
            return semantic("condition must be bool: %s",
                    e.condition().pos());

        if (cg.a() instanceof LiteralExpression le)
            e.cond().set((BoolLiteral) le.literal());

        visit(e.updater());
        return e;
    }

    private ForStatement forIterable(
            IterableForStatement e, ArrayTypeDeclarer atd) {
        var args = e.arguments();
        if (args.size() > 2)
            return semantic("can't over 2 receivers: %s",
                    args.get(2).pos());

        if (args.size() < 2) {
            context.putVar(Variable.newArg(args.getFirst(), atd.element()));
            return e;
        }

        var i = args.getLast();
        context.putVar(Variable.newArg(i, Primitive.INT.declarer(i.pos())));
        context.putVar(Variable.newArg(args.getFirst(), atd.element()));
        return e;
    }

    private ForStatement forIterable(
            IterableForStatement e, DefinitionDeclarer dtd) {
        if (!(dtd.def() instanceof EnumDefinition ed)) {
            return semantic("no iterable implement %s: %s",
                    dtd.def(), e.iterable().pos());
        }

        if (e.arguments().size() > 1)
            return semantic("can only be 1 receiver: %s",
                    e.arguments().get(1).pos());
        var a = e.arguments().getFirst();
        var t = new DerivedTypeDeclarer(a.pos(), new DerivedType(a.pos(),
                ed.symbol(), TypeArguments.EMPTY), Optional.empty());
        t.def.set(ed);
        var v = new Variable(a.pos(), Modifier.empty(), Declare.CONST, a,
                Lazy.of(t), Lazy.nil());
        context.putVar(v);
        return e;
    }

    public ForStatement visit(IterableForStatement e) {
        var g = optimize(e.iterable());
        e.iterable(g.a());
        if (g.b() instanceof ArrayTypeDeclarer atd)
            return forIterable(e, atd);

        if (g.b() instanceof DefinitionDeclarer dtd)
            return forIterable(e, dtd);

        return semantic("no iterable implement %s: %s",
                g.b(), e.iterable().pos());

    }

    public Statement visit(GotoStatement e) {
        checkLabel(e.label());
        return e;
    }

    public Statement visit(IfStatement e) {
        context.enterScope();
        visit(e.init());

        var g = optimize(e.condition());
        if (!g.b().isBool()) {
            return semantic("if condition must be bool: %s",
                    e.condition().pos());
        }
        e.condition(g.a());
        if (g.a() instanceof LiteralExpression le)
            e.cond().set((BoolLiteral) le.literal());

        visit(e.yes());
        visit(e.not());
        context.exitScope(e);
        return e;
    }

    public Statement visit(ReturnStatement e) {
        assert enterProc != null;
        e.procedure().set(enterProc);
        e.local(context.local().toList());
        var prot = enterProc.prototype();
        var pr = prot.returnSet();
        if (pr.none()) {
            if (e.result().none()) return e;
            return semantic("no result types");
        }
        if (e.result().none())
            return semantic("has result types");

        var er = e.result().get();
        var g = optimize(er);
        e.result(Optional.of(g.a()));
        if (assignable(pr.get(), g.b(), e.result(), true)) return e;
        return semantic("return not compatible: %s", e.pos());
    }

    private void checkConstantInteger(SwitchStatement s) {
        var set = new HashMap<IntegerLiteral, IntegerLiteral>();
        for (var b : s.branches()) {
            var constants = new ArrayList<Expression>(b.constants().size());
            for (var c : b.constants()) {
                var lit = calcInteger(c);
                var old = set.putIfAbsent(lit, lit);
                if (old != null) {
                    semantic("duplicate constants '%s': %s <--> %s",
                            c, c.pos(), old.pos());
                    continue;
                }
                constants.add(new LiteralExpression(c.pos(), lit));
            }
            b.constants(constants);
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
                        semantic("must be enum: %s", le.pos());
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

        var except = subtract(type.values().keys(), set.keys());
        if (except.isEmpty()) {
            if (s.defaultBranch().none()) return;
            semantic("'default' was unreachable when all covered: %s",
                    s.defaultBranch().get().pos());
        } else {
            if (s.defaultBranch().has()) return;
            semantic("covered missing %s %s: %s", type.symbol(),
                    except, s.value().pos());
        }
    }

    public Statement visit(SwitchStatement e) {
        context.enterScope();
        if (e.init().has()) visit(e.init().get());

        var cv = e.value();
        var g = optimize(cv);
        e.value(g.a());
        boolean ok = false;
        if (g.b() instanceof PrimitiveTypeDeclarer ptd) {
            if (ptd.primitive().isInteger()) {
                checkConstantInteger(e);
                ok = true;
            }
        } else if (g.b() instanceof DerivedTypeDeclarer dtd) {
            var dt = visit(dtd.derivedType());
            if (dt instanceof EnumDefinition ed) {
                checkConstantEnum(e, ed);
                ok = true;
            }
        }
        if (!ok) {
            return semantic("value require integer or enum: %s",
                    cv.pos());
        }

        for (var br : e.branches()) visit(br.body());

        e.defaultBranch().use(br -> visit(br.body()));

        context.exitScope(e);
        return e;
    }

    public Statement visit(ThrowStatement e) {
        assert enterProc != null;
        e.procedure().set(enterProc);
        e.local(context.local().toList());
        var g = optimize(e.exception());
        e.exception(g.a());
        return e;
    }

    public Statement visit(TryStatement e) {
        visit(e.body());
        visit(e.catchClauses());
        visit(e.finallyClause());
        return e;
    }

    public Statement visit(CatchClause e) {
        context.enterScope();
        var v = e.argument();
        visit(v.modifier());
        if (e.typeSet().size() > 1) {
            // TODO: 推导
            return unsupported("catch multi types: %s",
                    e.typeSet().get(1).pos());
        } else {
            v.type().set(e.typeSet().getFirst());
        }
        for (var td : e.typeSet()) visit(td);
        context.putVar(v);
        visit(e.body());
        context.exitScope(e);
        return e;
    }


    //

    private boolean checkConst(Expression e) {
        if (e instanceof PrimaryExpression pe)
            return checkConst(pe);
        return unreachable();
    }

    private boolean checkConst(MemberOfExpression e) {
        var g = optimize((Expression) e);
        var isValue = g.b().maybeRefer().none();
        var f = ((MemberOfExpression) g.a()).field();
        if (f.none()) {
            return semantic("operable field not found %s: %s",
                    e.member(), e.member().pos());
        }

        if (f.get().type().maybeRefer().has()) return false;
        if (f.get() instanceof ClassField cf) {
            if (cf.declare() == Declare.CONST) return true;
        }
        return isValue && checkConst(e.subject());
    }

    private boolean checkConst(CallExpression e) {
        var g = optimize(e);
        if (g.b() instanceof VoidTypeDeclarer) {
            return semantic("callable %s has no return: %s",
                    e.callee(), e.pos());
        }
        var ref = g.b().maybeRefer();
        return ref.none() || ref.get().immutable();
    }

    private boolean checkConst(Variable v) {
        var isValue = v.type().must().maybeRefer().none();
        return isValue && (v.declare() == Declare.CONST);
    }

    private boolean checkConst(ReferExpression e) {
        var o = context.findVar(e.symbol());
        if (o.none()) return semantic("%s not declared", e.symbol());
        return checkConst(o.get());
    }

    private boolean checkConst(AssertExpression e) {
        var im = e.type().maybeRefer().match(Refer::immutable);
        return im || checkConst(e.subject());
    }

    private boolean checkConst(PrimaryExpression e) {
        return switch (e) {
            case CallExpression ee -> checkConst(ee);
            case IndexOfExpression ee -> checkConst(ee.subject());
            case MemberOfExpression ee -> checkConst(ee);
            case ParenExpression ee -> checkConst(ee.child());
            case ReferExpression ee -> checkConst(ee);
            case VariableExpression ee -> checkConst(ee.variable());
            case AssertExpression ee -> checkConst(ee);
            case NewExpression ee -> false;
            case CurrentExpression ee -> false;
            case ObjectExpression ee -> true;
            case ArrayExpression ee -> true;
            case SizeofExpression ee -> true;
            case LiteralExpression ee -> true;
            case null, default -> unreachable();
        };
    }

    private Groups.G2<Operand, TypeDeclarer> optimize(Operand o) {
        Groups.G2<Operand, TypeDeclarer> g = switch (o) {
            case IndexOperand io -> optimize(io);
            case FieldOperand fo -> optimize(fo);
            case VariableOperand vo -> optimize(vo);
            case null, default -> unreachable();
        };
        g.a().type.set(g.b());
        return g;
    }

    private Groups.G2<Operand, TypeDeclarer> optimize(IndexOperand io) {
        var ig = optimize(io.index());
        if (ig.b() instanceof PrimitiveTypeDeclarer ptd) {
            if (!ptd.primitive().isInteger()) {
                return semantic("index require integer: %s", io.index().pos());
            }
        } else if (ig.b() instanceof LiteralTypeDeclarer ltd) {
            if (!ltd.isInteger()) {
                return semantic("index require integer: %s", io.index().pos());
            }
        }

        var sg = optimize(io.subject());
        if (!(sg.b() instanceof ArrayTypeDeclarer td)) {
            return semantic("require array: %s", io.pos());
        }
        if (td.refer().has()) {
            if (td.refer().get().immutable()) {
                return semantic("immutable array: %s", io.pos());
            }
        } else {
            if (checkConst(sg.a())) {
                return semantic("immutable array: %s", io.pos());
            }
        }

        var n = new IndexOperand(io.pos(), (PrimaryExpression) sg.a(), ig.a());
        return Groups.g2(n, td.element());
    }

    private Groups.G2<Operand, TypeDeclarer> optimize(FieldOperand fo) {
        var name = fo.field();

        var sg = optimize(fo.subject());
        var td = sg.b();
        if (td instanceof VoidTypeDeclarer) return unreachable();

        if (!(td instanceof DerivedTypeDeclarer dtd))
            return semantic("illegal operand %s: %s", name, fo.pos());
        if (!dtd.derivedType().generic().isEmpty()) return unreachable();

        var def = visit(dtd.derivedType());
        Optional<? extends Field> of = switch (def) {
            case StructureDefinition sd -> sd.fields().tryGet(name);
            case ClassDefinition cd -> cd.allFields().tryGet(name);
            case null, default -> semantic("%s have no any field: %s",
                    def, fo.pos());
        };

        if (of.none())
            return semantic("field %s not defined: %s", name, name.pos());
        var f = of.get();
        if (f instanceof ClassField cf && cf.declare() == Declare.CONST)
            return semantic("immutable field: %s", name.pos());

        var im = td.maybeRefer().none() && checkConst(sg.a());
        if (im) return semantic("immutable operand: %s", fo.pos());

        var s = (PrimaryExpression) sg.a();
        var n = new FieldOperand(fo.pos(), s, name);
        return Groups.g2(n, f.type());
    }

    private Groups.G2<Operand, TypeDeclarer> optimize(VariableOperand vo) {
        var s = vo.symbol();
        var o = context.findVar(s);
        if (o.has()) {
            var v = o.get();
            if (v.declare() == Declare.CONST)
                return semantic("immutable operand %s: %s", s, s.pos());
            if (context.isVarLocked(v))
                return semantic("readonly operand %s: %s", s, s.pos());

            vo.variable().set(o);
            return Groups.g2(vo, v.type().must());

        }

        if (enterClass != null && s.module().none()) {
            var f = enterClass.allFields().tryGet(s.name());
            if (f.has()) {
                if (f.get().declare() == Declare.CONST)
                    return semantic("immutable operand %s: %s", s, s.pos());
                var n = new FieldOperand(vo.pos(), newThis(s.pos()), s.name());
                return Groups.g2(n, f.get().type());
            }
        }
        return semantic("undefined operand %s: %s", s, s.pos());
    }

    private Expression defaultValue(TypeDeclarer t, Entity v) {
        if (t.maybeRefer().has()) {
            var e = new LiteralExpression(new NilLiteral(v.pos()));
            e.resultType.set(t);
            return e;
        }

        Expression e;
        if (t instanceof DerivedTypeDeclarer dtd) {
            var def = visit(dtd.derivedType());
            if (def instanceof ClassDefinition ||
                    def instanceof StructureDefinition) {
                e = new ObjectExpression(v.pos());
            } else if (def instanceof EnumDefinition ed) {
                e = new EnumValueExpression(v.pos(),
                        ed, ed.values().getValue(0));
            } else if (def instanceof PrototypeDefinition) {
                e = new LiteralExpression(new NilLiteral(v.pos()));
            } else {
                return unreachable();
            }
        } else if (t instanceof PrimitiveTypeDeclarer ptd) {
            var lit = switch (ptd.primitive().kind) {
                case INTEGER -> new IntegerLiteral(v.pos(), 0);
                case FLOAT -> new FloatLiteral(v.pos(), BigDecimal.ZERO);
                case BOOL -> new BoolLiteral(v.pos(), false);
            };
            e = new LiteralExpression(v.pos(), lit);
        } else if (t instanceof FuncTypeDeclarer) {
            e = new LiteralExpression(new NilLiteral(v.pos()));
        } else if (t instanceof ArrayTypeDeclarer) {
            e = new ArrayExpression(v.pos(), List.of());
        } else {
            return unreachable();
        }
        e.resultType.set(t);
        return e;
    }


    //
    // expression and tuple
    //


    public Groups.G2<List<Expression>, List<TypeDeclarer>>
    optimize(List<Expression> list) {
        var vs = new ArrayList<Expression>(list.size());
        var ts = new ArrayList<TypeDeclarer>(list.size());
        for (var v : list) {
            var g = optimize(v);
            if (g.b() instanceof DefinitionDeclarer) {
                return semantic("require value, not type %s: %s",
                        g.b(), v.pos());
            }
            vs.add(g.a());
            ts.add(g.b());
        }
        return Groups.g2(vs, ts);
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(Expression e) {
        Groups.G2<Expression, TypeDeclarer> g = switch (e) {
            case BinaryExpression ee -> optimize(ee);
            case UnaryExpression ee -> optimize(ee);
            case ArrayExpression ee -> optimize(ee);
            case AssertExpression ee -> optimize(ee);
            case SizeofExpression ee -> optimize(ee);
            case CallExpression ee -> optimize(ee);
            case ConvertExpression ee -> optimize(ee);
            case CurrentExpression ee -> optimize(ee);
            case IndexOfExpression ee -> optimize(ee);
            case LambdaExpression ee -> optimize(ee);
            case LiteralExpression ee -> optimize(ee);
            case MemberOfExpression ee -> optimize(ee);
            case NewExpression ee -> optimize(ee);
            case ObjectExpression ee -> optimize(ee);
            case PairsExpression ee -> optimize(ee);
            case ParenExpression ee -> optimize(ee);
            case ReferExpression ee -> optimize(ee);
            case ClosureExpression ee -> optimize(ee);
            case VariableExpression ee -> Groups.g2(ee,
                    ee.variable().type().must());
            case null, default -> unreachable();
        };
        g.b().mappable(mappable(g.b()));
        g.a().resultType.set(g.b());
        g.a().expectCallable(e.expectCallable());
        return g;
    }

    private Groups.G2<Expression, TypeDeclarer>
    optimize(BinaryExpression e,
             LiteralExpression l,
             LiteralExpression r) {
        var op = e.operator();
        var lv = l.literal();
        var rv = r.literal();
        if (lv instanceof IntegerLiteral ill &&
                rv instanceof IntegerLiteral irl) {
            var lit = computer.calc(op, ill, irl);
            var td = lit instanceof IntegerLiteral ?
                    Primitive.INT : Primitive.BOOL;
            return Groups.g2(new LiteralExpression(e.pos(), lit),
                    td.declarer(e.pos()));
        }

        if (lv instanceof FloatLiteral fll &&
                rv instanceof FloatLiteral frl) {
            var lit = computer.calc(op, fll, frl);
            var td = lit instanceof FloatLiteral ?
                    Primitive.FLOAT : Primitive.BOOL;
            return Groups.g2(new LiteralExpression(e.pos(), lit),
                    td.declarer(e.pos()));
        }

        if (lv instanceof BoolLiteral bll &&
                rv instanceof BoolLiteral brl)
            return Groups.g2(new LiteralExpression(e.pos(),
                            computer.calc(op, bll, brl)),
                    Primitive.BOOL.declarer(e.pos()));

        if (lv instanceof StringLiteral sll &&
                rv instanceof StringLiteral srl) {
            var lit = computer.calc(op, sll, srl);
            var td = new LiteralTypeDeclarer(e.pos(), lit);
            return Groups.g2(new LiteralExpression(e.pos(), lit), td);
        }

        if (lv instanceof NilLiteral a &&
                rv instanceof NilLiteral b) {
            var lit = computer.calc(op, a, b);
            var td = new LiteralTypeDeclarer(e.pos(), lit);
            return Groups.g2(new LiteralExpression(e.pos(), lit), td);
        }

        return semantic("not support %s %s %s: %s", l, op, r, e.pos());
    }


    private Optional<TypeDeclarer> primitiveBinOp(
            TypeDeclarer l, TypeDeclarer r, BinaryExpression e) {
        var lk = primitiveKind(l);
        var rk = primitiveKind(r);
        if (lk.none() || rk.none())
            return Optional.empty();

        Primitive.Kind lp = lk.must().kind, rp = rk.must().kind;
        if (lp != rp)
            return semantic("require same type: %s", e.pos());

        var expect = l instanceof PrimitiveTypeDeclarer ? l : r;
        var op = e.operator();
        switch (lp) {
            case INTEGER -> {
                if (BinaryOperator.SetMath.contains(op) ||
                        BinaryOperator.SetBits.contains(op))
                    return Optional.of(expect);
                if (BinaryOperator.SetRel.contains(op))
                    return Optional.of(Primitive.BOOL.declarer(e.pos()));
            }
            case FLOAT -> {
                if (BinaryOperator.SetMath.contains(op))
                    return Optional.of(expect);
                if (BinaryOperator.SetRel.contains(op))
                    return Optional.of(Primitive.BOOL.declarer(e.pos()));
            }
            case BOOL -> {
                if (BinaryOperator.SetLogic.contains(op))
                    return Optional.of(expect);
            }
        }
        return Optional.empty();
    }

    private Groups.G2<Expression, TypeDeclarer> checkNilBinOp(
            Groups.G2<Expression, TypeDeclarer> g, BinaryExpression e) {
        if (g.b().maybeRefer().none())
            return semantic("nil must compare to a reference: %s", e.pos());

        if (e.operator() != BinaryOperator.EQ &&
                e.operator() != BinaryOperator.NE)
            return semantic("nil can't %s a reference: %s",
                    e.operator(), e.pos());

        boolean nil = e.operator() == BinaryOperator.EQ;
        var t = new PrimitiveTypeDeclarer(e.pos(), Primitive.BOOL, Optional.empty());
        var n = new IsNilExpression(e.pos(), g.a(), nil);
        return Groups.g2(n, t);
    }

    private Optional<EnumDefinition> findEnum(TypeDeclarer td) {
        if (td instanceof EnumTypeDeclarer etd)
            return Optional.of(etd.def());
        if (td instanceof DerivedTypeDeclarer dtd) {
            var def = visit(dtd.derivedType());
            if (def instanceof EnumDefinition ed)
                return Optional.of(ed);
        }
        return Optional.empty();
    }

    private boolean enumBinOp(
            TypeDeclarer l, TypeDeclarer r, BinaryExpression e) {
        var le = findEnum(l);
        if (le.none()) return false;
        var re = findEnum(r);
        if (re.none()) return false;

        return le.get().equals(re.get());
    }

    private boolean objectBinOp(
            DerivedTypeDeclarer l, DerivedTypeDeclarer r,
            BinaryExpression e) {
        visit(l.derivedType().generic());
        visit(r.derivedType().generic());
        var lt = visit(l.derivedType());
        var rt = visit(r.derivedType());
        if (lt instanceof ObjectDefinition lo &&
                rt instanceof ObjectDefinition ro)
            return assignable(lo, ro, true) ||
                    assignable(ro, lo, true);
        return false;
    }

    private boolean referCompare(
            TypeDeclarer l, TypeDeclarer r, BinaryExpression e) {
        if (mappable(l) && mappable(r)) {
            return true; // primitive, structure, and it's array
        }
        if (l instanceof DerivedTypeDeclarer ld &&
                r instanceof DerivedTypeDeclarer rd) {
            return objectBinOp(ld, rd, e);
        }
        if (l instanceof ArrayTypeDeclarer la &&
                r instanceof ArrayTypeDeclarer ra) {
            return assignable(la.element(), ra.element(),
                    Optional.empty(), false);
        }
        return false;
    }

    private Optional<TypeDeclarer> derivedTypeBinOp(
            TypeDeclarer l, TypeDeclarer r, BinaryExpression e) {
        if (e.operator() != BinaryOperator.EQ &&
                e.operator() != BinaryOperator.NE)
            return Optional.empty();

        Optional<TypeDeclarer> ret = Optional.of(
                Primitive.BOOL.declarer(e.pos()));

        if (enumBinOp(l, r, e)) return ret;

        var lr = l.maybeRefer();
        var rr = r.maybeRefer();
        if (lr.none() && rr.none()) // value type, directly compare
            return l.equals(r) ? ret : Optional.empty();

        if (lr.has() || rr.has()) {
            if (referCompare(l, r, e))
                return ret;
            if (l.isNil() || r.isNil())
                return ret;
        }

        return Optional.empty();
    }

    public Groups.G2<Expression, TypeDeclarer>
    optimize(BinaryExpression e) {
        var l = optimize(e.left());
        var r = optimize(e.right());
        if (l.a() instanceof LiteralExpression le &&
                r.a() instanceof LiteralExpression re) {
            return optimize(e, le, re);
        }

        if (l.b().isNil()) return checkNilBinOp(r, e);
        if (r.b().isNil()) return checkNilBinOp(l, e);

        var n = new BinaryExpression(e.pos(), e.operator(), l.a(), r.a());
        var t = primitiveBinOp(l.b(), r.b(), e);
        if (t.none()) t = derivedTypeBinOp(l.b(), r.b(), e);
        if (t.has()) return Groups.g2(n, t.get());

        return semantic("not support %s %s %s: %s",
                l.b(), e.operator(), r.b(), e.pos());
    }

    private Groups.G2<Expression, TypeDeclarer>
    optimize(UnaryExpression e, LiteralExpression le) {
        var op = e.operator();
        var l = le.literal();
        if (l instanceof IntegerLiteral il) {
            var r = switch (op) {
                case POSITIVE -> il.value();
                case NEGATIVE -> il.value().negate();
                case INVERT -> il.value().not();
            };
            var n = new LiteralExpression(e.pos(),
                    new IntegerLiteral(il.pos(),
                            r, il.radix()));
            return Groups.g2(n, Primitive.INT.declarer(e.pos()));
        }

        if (l instanceof FloatLiteral fl) {
            BigDecimal r = switch (op) {
                case POSITIVE -> fl.value();
                case NEGATIVE -> fl.value().negate();
                case INVERT -> semantic(
                        "float not support %s: %s",
                        op, e.pos());
            };
            var n = new LiteralExpression(e.pos(),
                    new FloatLiteral(fl.pos(), r));
            return Groups.g2(n, Primitive.FLOAT.declarer(e.pos()));
        }

        if (l instanceof BoolLiteral bl) {
            if (op == UnaryOperator.INVERT) {
                var n = new LiteralExpression(e.pos(),
                        bl.not());
                return Groups.g2(n, Primitive.BOOL.declarer(e.pos()));
            }
        }

        return unsupported("%s not support: %s",
                l.type(), op);
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(UnaryExpression e) {
        var o = optimize(e.operand());
        if (o.a() instanceof LiteralExpression le) {
            return optimize(e, le);
        }
        var n = new UnaryExpression(e.pos(), e.operator(), o.a());
        return Groups.g2(n, o.b());
    }

    private ObjectDefinition getObject(TypeDeclarer td) {
        if (td instanceof DerivedTypeDeclarer dtd) {
            var t = visit(dtd.derivedType());
            dtd.def.set(t);
            if (t instanceof ObjectDefinition cd)
                return cd;
        }
        return semantic("require class or interface: %s", td.pos());
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(AssertExpression e) {
        var g = optimize(e.subject());
        if (!(g.b() instanceof DerivedTypeDeclarer srcType)) {
            return semantic("assert can't used for %s: %s", g.b(), e.type());
        }
        var sr = srcType.refer();
        if (sr.none()) {
            return semantic("require a refer: %s", e.subject().pos());
        }

        var dstType = e.type();
        var dr = dstType.maybeRefer();
        if (dr.none()) return semantic(
                "type must refer: %s", e.type().pos());

        if (!referable(dr.get(), sr, Optional.of(g.a())))
            return unreachable();

        var n = new AssertExpression(e.pos(),
                (PrimaryExpression) g.a(), e.type());
        var srcDef = getObject(srcType);
        var tgtDef = getObject(dstType);
        if (assignable(tgtDef, srcDef, true)) {
            return Groups.g2(n, dstType);
        }
        if (tgtDef instanceof InterfaceDefinition ||
                srcDef instanceof InterfaceDefinition) {
            n.needCheck(true);
            return Groups.g2(n, dstType);
        }
        if (tgtDef instanceof ClassDefinition &&
                srcDef instanceof ClassDefinition) {
            if (assignable(srcDef, tgtDef, true)) {
                n.needCheck(true);
                return Groups.g2(n, dstType);
            }
        }

        return semantic("inconvertible types %s to %s: %s",
                srcType, dstType, e.pos());
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(SizeofExpression se) {
        enablePhantom = true;
        visit(se.type());

        var size = estimateSize(se.type());
        se.size(size);
        var n = new LiteralExpression(se.pos(), new IntegerLiteral(se.pos(), size));
        return Groups.g2(n, Primitive.INT.declarer(se.pos()));
    }

    private Optional<Prototype> checkCallee(TypeDeclarer td) {
        if (td instanceof FuncTypeDeclarer ftd) {
            return Optional.of(ftd.prototype());
        }
        if (td instanceof DerivedTypeDeclarer dtd) {
            var def = visit(dtd.derivedType());
            if (def instanceof PrototypeDefinition pd) {
                return Optional.of(pd.prototype());
            }
        }
        return Optional.empty();
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(CallExpression e) {
        var call = e.callee();
        var args = e.arguments();

        call.expectCallable(true);
        var g = optimize(call);
        var op = checkCallee(g.b());
        if (op.none()) {
            return semantic("%s not callable: %s", call, e.pos());
        }

        var rg = optimize(args);
        var left = op.get().parameterSet().types();
        if (!assignable(left, rg.b(), rg.a()))
            return semantic("arguments not compatible");

        call = (PrimaryExpression) g.a();
        var n = new CallExpression(e.pos(), call, rg.a());
        n.prototype().set(op);
        if (op.get().returnSet().none())
            return Groups.g2(n, new VoidTypeDeclarer(op.get().pos()));

        return Groups.g2(n, op.get().returnSet().get());
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(ConvertExpression e) {
        // explicit convert
        var p = e.primitive();
        var g = optimize(e.operand());
        if (g.a() instanceof LiteralExpression le) {
            // 字面量转换
            var td = p.declarer(e.pos());
            var lit = le.literal();
            if (lit instanceof BoolLiteral bl) {
                if (p == Primitive.BOOL) {
                    var n = new LiteralExpression(e.pos(), bl);
                    return Groups.g2(n, td);
                }
            } else if (lit instanceof IntegerLiteral il) {
                if (p == Primitive.INT) {
                    var n = new LiteralExpression(e.pos(), il);
                    return Groups.g2(n, td);
                }
                if (p.isInteger() || p.isFloat()) {
                    var c = new ConvertExpression(e.pos(), p, g.a());
                    return Groups.g2(c, td);
                }
            } else if (lit instanceof FloatLiteral fl) {
                if (p == Primitive.FLOAT) {
                    var n = new LiteralExpression(e.pos(), fl);
                    return Groups.g2(n, td);
                }
                if (p.isFloat() || p.isInteger()) {
                    var c = new ConvertExpression(e.pos(), p, g.a());
                    return Groups.g2(c, td);
                }
            }
            return semantic("inconvertible %s(%s): %s",
                    p, lit, e.pos());
        }
        if (g.b() instanceof PrimitiveTypeDeclarer td) {
            // 基本类型就返回转换表达式：`bool`
            if (e.primitive().isBool() == td.primitive().isBool()) {
                if (e.primitive().isBool()) {
                    // `bool`省略表达式
                    return Groups.g2(g.a(), g.b());
                }
                // 数值需要转
                var n = new ConvertExpression(e.pos(), e.primitive(), g.a());
                return Groups.g2(n, e.primitive().declarer(e.pos()));
            }
        }

        return semantic("can't convert: %s", e.operand().pos());
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(CurrentExpression e) {
        // 必定是在类的方法里
        var dt = new DerivedType(e.pos(), e.type(), TypeArguments.EMPTY);
        var ref = new Refer(e.pos(), PHANTOM, false, false);
        var td = new DerivedTypeDeclarer(e.pos(), dt, Optional.of(ref));
        td.def.set(enterClass);
        return Groups.g2(e, td);
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(IndexOfExpression e) {
        var sg = optimize(e.subject());
        var ig = optimize(e.index());

        if (!isInteger(ig.b()))
            return semantic("index require integer: %s", e.pos());

        IntegerLiteral idx = null;
        if (ig.a() instanceof LiteralExpression lit) {
            idx = (IntegerLiteral) lit.literal();
            if (idx.compareTo(0) < 0) {
                return semantic("negative index: %s", e.index().pos());
            }
        }

        if (sg.b() instanceof DefinitionDeclarer dtd) {
            if (dtd.def() instanceof EnumDefinition ed) {
                Expression n;
                if (idx != null) {
                    if (idx.compareTo(ed.values().size()) >= 0) {
                        return semantic("index out of bounds: %s", e.pos());
                    }
                    var ev = ed.ofId(idx.value().intValue());
                    n = new EnumValueExpression(e.pos(), ed, ev);
                } else {
                    n = new EnumIdExpression(e.pos(), ed, ig.a());
                }
                var td = new EnumTypeDeclarer(e.pos(), ed);
                return Groups.g2(n, td);
            }
            return semantic("only enum type can use index: %s", e.pos());
        }

        if (sg.b() instanceof ArrayTypeDeclarer atd) {
            var a = (PrimaryExpression) sg.a();
            if (atd.length().has() && idx != null) {
                if (idx.compareTo(atd.len()) >= 0) {
                    return semantic("index out of bounds: %s", e.pos());
                }
            }
            var ie = ig.a();
            if (idx != null) {
                ie = new LiteralExpression(e.pos(), idx);
            }
            var n = new IndexOfExpression(e.pos(), a, ie);
            return Groups.g2(n, atd.element());
        }

        return semantic("%s not implement index: %s",
                sg.b(), e.subject().pos());
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(LambdaExpression e) {
        return unsupported("lambda");
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(LiteralExpression e) {
        return Groups.g2(e, new LiteralTypeDeclarer(e.pos(), e.literal()));
    }

    private Groups.G2<Expression, TypeDeclarer>
    optimizeEnum(MemberOfExpression e, EnumDefinition ed) {
        var name = e.member();
        var v = ed.values().tryGet(name);
        if (v.none()) {
            return semantic("%s.%s not define: %s",
                    ed, name, name.pos());
        }
        // 转换成枚举类型
        var n = new EnumValueExpression(e.pos(), ed, v.get());
        var td = new EnumTypeDeclarer(e.pos(), ed);
        return Groups.g2(n, td);
    }

    private Groups.G2<Expression, TypeDeclarer>
    optimizeArray(Expression s, ArrayTypeDeclarer atd, Identifier name) {
        var af = atd.getField(name);
        if (af.has()) {
            var td = Primitive.INT.declarer(s.pos());
            if (atd.length().has()) {
                var lit = new IntegerLiteral(s.pos(), atd.len());
                var n = new LiteralExpression(s.pos(), lit);
                return Groups.g2(n, td);
            }
            var n = new ArrayLenExpression(s.pos(), atd);
            return Groups.g2(n, td);
        }
        return semantic("array has no field %s: %s",
                name, name.pos());
    }

    private Groups.G2<Expression, TypeDeclarer>
    optimizeMember(PrimaryExpression s, TypeDefinition def,
                   Identifier name, TypeArguments generic) {
        if (def instanceof StructureDefinition sd) {
            var f = sd.field(name);
            if (f.has()) {
                var n = new MemberOfExpression(s.pos(), s, name, generic, f.get());
                return Groups.g2(n, f.get().type());
            }
        } else if (def instanceof ClassDefinition cd) {
            if (s.expectCallable()) {
                var m = cd.allMethods().tryGet(name);
                if (m.has()) {
                    var n = new MemberOfExpression(s.pos(), s, name, generic);
                    var td = new FuncTypeDeclarer(s.pos(), m.get().prototype(),
                            generic, m);
                    return Groups.g2(n, td);
                }
            } else {
                var f = cd.allFields().tryGet(name);
                if (f.has()) {
                    var n = new MemberOfExpression(s.pos(), s, name, generic, f.get());
                    return Groups.g2(n, f.get().type());
                }
            }
        } else if (def instanceof InterfaceDefinition id) {
            var m = id.allMethods.tryGet(name);
            if (m.has()) {
                var n = new MemberOfExpression(s.pos(), s, name, generic);
                var td = new FuncTypeDeclarer(s.pos(), m.get().prototype(),
                        generic, m);
                return Groups.g2(n, td);
            }
        } else if (def instanceof EnumDefinition ed) {
            var f = ed.getField(name);
            if (f.has()) {
                var n = new MemberOfExpression(s.pos(), s, name, generic, f.get());
                return Groups.g2(n, f.get().type());
            }
        }

        return semantic("member '%s'.'%s' not defined: %s", def, name, s.pos());
    }

    private Groups.G2<Expression, TypeDeclarer>
    optimizeEnumValue(EnumValueExpression eve, EnumTypeDeclarer etd, Identifier name) {
        var o = etd.def().getField(name);
        if (o.none()) return semantic("%s has no field %s: %s",
                etd.def(), name, name.pos());

        var v = eve.value();
        switch (name.value()) {
            case EnumDefinition.TokenFieldId -> {
                var lit = new IntegerLiteral(v.pos(), v.id());
                var n = new LiteralExpression(v.pos(), lit);
                var td = Primitive.INT.declarer(name.pos());
                return Groups.g2(n, td);
            }
            case EnumDefinition.TokenFieldValue -> {
                var lit = new IntegerLiteral(v.pos(), v.val());
                var n = new LiteralExpression(v.pos(), lit);
                var td = Primitive.INT.declarer(name.pos());
                return Groups.g2(n, td);
            }
            case EnumDefinition.TokenFieldName -> {
                var lit = new StringLiteral(v.pos(), US_ASCII, v.name().value().getBytes());
                var n = new LiteralExpression(v.pos(), lit);
                var td = new LiteralTypeDeclarer(v.pos(), lit);
                return Groups.g2(n, td);
            }
        }
        return semantic("%s has no field %s: %s",
                etd.def(), name, name.pos());
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(MemberOfExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");
        var sg = optimize(e.subject());

        if (sg.b() instanceof DefinitionDeclarer dtd) {
            if (!(dtd.def() instanceof EnumDefinition ed))
                return semantic("%s not support use member: %s", dtd, e.pos());
            return optimizeEnum(e, ed);
        }

        if (sg.b() instanceof ArrayTypeDeclarer atd) {
            return optimizeArray(sg.a(), atd, e.member());
        }

        if (sg.b() instanceof DerivedTypeDeclarer dtd) {
            var def = visit(dtd.derivedType());
            var s = (PrimaryExpression) sg.a();
            s.expectCallable(e.expectCallable());
            return optimizeMember(s, def, e.member(), e.generic());
        }

        if (sg.b() instanceof EnumTypeDeclarer etd) {
            return optimizeEnumValue((EnumValueExpression) sg.a(), etd, e.member());
        }

        return semantic("member '%s' not defined: %s",
                e.member(), e.pos());
    }

    public TypeDeclarer optimize(NewArrayType e) {
        visit(e.element());

        var ref = new Refer(e.pos(), STRONG, false, false);
        var lg = optimize(e.length());
        if (lg.b() instanceof PrimitiveTypeDeclarer ptd &&
                ptd.primitive().isInteger() ||
                lg.b() instanceof LiteralTypeDeclarer ltd &&
                        ltd.isInteger()) {
            return new ArrayTypeDeclarer(e.pos(), e.element(),
                    Optional.empty(), Optional.of(ref));
        }

        return semantic("array length must be integer: %s",
                e.length().pos());
    }

    public TypeDeclarer optimize(NewDefinedType e) {
        var ref = new Refer(e.pos(), STRONG, false, false);
        if (e.type() instanceof PrimitiveType pt) {
            return new PrimitiveTypeDeclarer(e.pos(),
                    pt.primitive(), Optional.of(ref));
        }
        if (e.type() instanceof DerivedType dt) {
            var def = visit(dt);
            if (def instanceof StructureDefinition ||
                    def instanceof ClassDefinition) {
                var n = new DerivedTypeDeclarer(e.pos(), dt,
                        Optional.of(ref));
                n.def.set(def);
                return n;
            }
        }
        return semantic("can't new type %s: %s",
                e.type(), e.type().pos());
    }

    public TypeDeclarer optimize(NewType e) {
        return switch (e) {
            case NewArrayType ee -> optimize(ee);
            case NewDefinedType ee -> optimize(ee);
            case null, default -> unreachable();
        };
    }

    public boolean newInitializable(
            NewExpression e, TypeDeclarer dstType,
            TypeDeclarer argType, Expression arg) {
        if (e.type() instanceof NewDefinedType ndt) {
            if (ndt.type() instanceof PrimitiveType pt) {
                if (argType instanceof PrimitiveTypeDeclarer ptd) {
                    // 初始化检查
                    return pt.primitive() == ptd.primitive();
                } else if (argType instanceof LiteralTypeDeclarer ltd) {
                    return ltd.literal().compatible()
                            .match(k -> k.kind == pt.primitive().kind);
                }
            } else if (ndt.type() instanceof DerivedType dt) {
                var dtd = (DerivedTypeDeclarer) dstType;
                // 定义类型检查
                if (argType instanceof DerivedTypeDeclarer atd) {
                    return dt.equals(atd.derivedType());
                } else if (argType instanceof ObjectTypeDeclarer atd) {
                    // 对象初始化表达式
                    return initializable(dtd, atd, (ObjectExpression) arg);
                }
            }
        } else if (e.type() instanceof NewArrayType nat) {
            if (argType instanceof ArrayTypeDeclarer atd) {
                var net = nat.element();
                var aet = atd.element();
                if (!(arg instanceof ArrayExpression ae))
                    return assignable(net, aet, Optional.empty());

                var i = 0;
                for (var ev : ae.elements()) {
                    if (assignable(net, aet, Optional.of(ev))) {
                        i++;
                        continue;
                    }
                    return semantic(
                            "array element at %d not match type %s: %s",
                            i, nat.element(), ev.pos());
                }
                return true;
            }
        }

        return false;
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(NewExpression e) {
        var dstType = optimize(e.type());
        var ea = e.arg().map(this::optimize);
        if (ea.none()) {
            var n = new NewExpression(e.pos(), e.type(), Optional.empty());
            return Groups.g2(n, dstType);
        }
        var ag = ea.get();
        var arg = ag.a();
        var argType = ag.b();
        var n = new NewExpression(e.pos(), e.type(), Optional.of(arg));

        if (newInitializable(e, dstType, argType, arg)) {
            return Groups.g2(n, dstType);
        }

        return semantic("new-type %s and init-type %s not match: %s",
                e.type(), argType, arg.pos());
    }


    private Groups.G2<Prototype, PrimaryExpression>
    findCallable(ReferExpression re) {
        visit(re.generic());
        var s = re.symbol();

        var ov = context.findVar(s);
        if (ov.has()) {
            var prot = checkCallee(ov.get().type().must());
            if (prot.has()) return Groups.g2(prot.get(), re);
        }

        var od = context.findFunc(s);
        if (od.has()) return Groups.g2(od.get().prototype(), re);

        if (s.module().none() && enterClass != null) {
            var om = enterClass.allMethods().tryGet(s.name());
            if (om.has()) {
                var n = wrapThis(re, om.get());
                return Groups.g2(om.get().prototype(), n);
            }
            var of = enterClass.allFields().tryGet(s.name());
            if (of.has()) {
                var prot = checkCallee(of.get().type());
                if (prot.has()) return Groups.g2(prot.get(), re);
            }
        }

        return semantic("func/method %s not found: %s",
                s, re.pos());
    }

    public Groups.G2<Expression, TypeDeclarer> findVariable(ReferExpression re) {
        var s = re.symbol();
        var o = context.findVar(s);
        if (o.has()) {
            var v = o.get();
            if (v.declare() == Declare.CONST &&
                    v.value().match(e -> e instanceof LiteralExpression))
                return Groups.g2(v.value().must(), v.type().must());
            var n = new VariableExpression(re.pos(), v);
            return Groups.g2(n, v.type().must());
        }

        var f = context.findFunc(s);
        if (f.has()) {
            var td = new FuncTypeDeclarer(re.pos(), f.get().prototype(),
                    re.generic());
            return Groups.g2(re, td);
        }

        var t = context.findType(s);
        if (t.has()) {
            var td = new DefinitionDeclarer(s.pos(), t.get());
            return Groups.g2(re, td);
        }

        if (enterClass != null && s.module().none()) {
            var of = enterClass.allFields().tryGet(s.name());
            if (of.has()) {
                return Groups.g2(wrapThis(re.symbol(), of.get()), of.get().type());
            }
        }

        return semantic("undefined symbol '%s': %s", s, s.pos());
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(ReferExpression e) {
        visit(e.generic());

        if (e.expectCallable()) {
            var g = findCallable(e);
            var td = new FuncTypeDeclarer(e.pos(), g.a(), e.generic());
            return Groups.g2(e, td);
        }

        return findVariable(e);
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(ArrayExpression e) {
        var es = e.elements();
        if (es.isEmpty()) {
            var l = new LiteralExpression(e.pos(), new IntegerLiteral(e.pos(), 0));
            var t = new ArrayTypeDeclarer(e.pos(), new VoidTypeDeclarer(e.pos()),
                    Optional.of(l), Optional.empty());
            return Groups.g2(e, t);
        }

        var values = new ArrayList<Expression>(e.elements().size());
        TypeDeclarer type = null;
        var i = 0;
        for (var v : es) {
            var g = optimize(v);
            values.add(g.a());
            if (type == null) {
                type = g.b();
            } else {
                if (!assignable(type, g.b(), Optional.of(g.a()))) {
                    return semantic("incompatible type of elements %s <--> %s: %s",
                            es.getFirst(), es.get(i), es.get(i).pos());
                }
            }
            i++;
        }

        var length = new IntegerLiteral(e.pos(), es.size());
        var le = new LiteralExpression(e.pos(), length);
        var atd = new ArrayTypeDeclarer(e.pos(), type,
                Optional.of(le), Optional.empty());
        atd.len(es.size());

        var n = new ArrayExpression(e.pos(), values);
        return Groups.g2(n, atd);
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(ObjectExpression oe) {
        var entries = new IdentifierTable<Expression>(oe.entries().size());
        var types = new IdentifierTable<TypeDeclarer>(oe.entries().size());
        for (var n : oe.entries().nodes()) {
            var g = optimize(n.value());
            entries.add(n.key(), g.a());
            types.add(n.key(), g.b());
        }
        var n = new ObjectExpression(oe.pos(), entries);
        var t = new ObjectTypeDeclarer(oe.pos(), types);
        return Groups.g2(n, t);
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(ParenExpression e) {
        return optimize(e.child());
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(ClosureExpression e) {
        return unsupported("closure");
    }

    public Groups.G2<Expression, TypeDeclarer> optimize(PairsExpression e) {
        return unsupported("pairs");
    }

    private Optional<IntegerLiteral> tryCalcInteger(Expression s) {
        var g = optimize(s);
        if (g.a() instanceof LiteralExpression le &&
                le.literal() instanceof IntegerLiteral il) {
            return Optional.of(il);
        }
        return Optional.empty();
    }

    private IntegerLiteral calcInteger(Expression e) {
        var v = tryCalcInteger(e);
        if (v.has()) return v.get();
        return semantic("require integer: %s %s", e, e.pos());
    }

    private IntegerLiteral calcSize(Expression e) {
        var g = optimize(e);
        if (g.a() instanceof LiteralExpression le &&
                le.literal() instanceof IntegerLiteral il &&
                il.value().compareTo(BigInteger.ZERO) >= 0) {
            return il;
        }
        return semantic("require non-negative integer: %s", e);
    }


    //

    // split change
    private VariableExpression makeTemp(
            PrimaryExpression e, ArrayList<Variable> s) {
        var vn = new Identifier(e.pos(), "feng$tmp");
        var v = new Variable(e.pos(), Modifier.empty(), Declare.CONST,
                vn, e.resultType, Lazy.of(e));
        s.add(v);
        return new VariableExpression(e.pos(), v);
    }

    private Expression splitChain(
            Expression e, ArrayList<Variable> s) {
        if (e instanceof PrimaryExpression pe) {
            return splitChain(pe, s);
        }
        if (e instanceof BinaryExpression be) {
            var l = splitChain(be.left(), s);
            var r = splitChain(be.right(), s);
            return new BinaryExpression(be.pos(),
                    be.operator(), l, r);
        }
        if (e instanceof UnaryExpression ue) {
            var o = splitChain(ue.operand(), s);
            return new UnaryExpression(ue.pos(),
                    ue.operator(), o);
        }
        return e;
    }

    private PrimaryExpression splitChain(
            PrimaryExpression e, ArrayList<Variable> s) {
        if (e instanceof NewExpression ee) {
            return makeTemp(ee, s);
        }
        if (e instanceof CallExpression ee) {
            var c = splitChain(ee.callee(), s);
            return makeTemp(c, s);
        }
        if (e instanceof MemberOfExpression ee) {
            var c = splitChain(ee.subject(), s);
            return new MemberOfExpression(ee.pos(), c,
                    ee.member(), ee.generic());
        }
        if (e instanceof IndexOfExpression ee) {
            var i = splitChain(ee.index(), s);
            var c = splitChain(ee.subject(), s);
            return new IndexOfExpression(ee.pos(), c, i);
        }
        if (e instanceof AssertExpression ee) {
            var c = splitChain(ee.subject(), s);
            return new AssertExpression(ee.pos(), c, ee.type());
        }
        if (e instanceof ConvertExpression ee) {
            var c = splitChain(ee.operand(), s);
            return new ConvertExpression(ee.pos(),
                    ee.primitive(), c);
        }
        if (e instanceof ArrayExpression ee) {
            var list = new ArrayList<Expression>(ee.elements().size());
            for (var el : ee.elements()) {
                list.add(splitChain(el, s));
            }
            return new ArrayExpression(e.pos(), list);
        }
        if (e instanceof ObjectExpression ee) {
            var es = new IdentifierTable<Expression>(ee.entries().size());
            for (var n : ee.entries().nodes()) {
                es.add(n.key(), splitChain(n.value(), s));
            }
            return new ObjectExpression(e.pos(), es);
        }
        return e;
    }

    // attribute


    public Entity visit(AttributeDefinition ad) {
        return unsupported("attribute");
    }
}
