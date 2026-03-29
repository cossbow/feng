package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.AttributeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.ast.var.*;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.layout.LayoutTool;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.util.*;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.util.Stack;
import org.cossbow.feng.visit.SymbolContext;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.cossbow.feng.ast.Position.ZERO;
import static org.cossbow.feng.ast.dcl.ReferKind.PHANTOM;
import static org.cossbow.feng.ast.dcl.ReferKind.STRONG;
import static org.cossbow.feng.util.CommonUtil.subtract;
import static org.cossbow.feng.util.ErrorUtil.*;

public class SemanticAnalysis {

    private final StackedContext context;
    private final boolean low;

    private final LiteralComputer computer = new LiteralComputer();
    private final ReturnAnalyzer analyzer = new ReturnAnalyzer();

    public SemanticAnalysis(SymbolContext parent, boolean low) {
        context = new StackedContext(parent);
        this.low = low;
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
                analyse(ed);
                enums.add(ed);
            }
        }
        return enums;
    }

    private Optional<PrototypeDefinition>
    findPrototype(TypeDeclarer td) {
        if (td instanceof DerivedTypeDeclarer dtd) {
            var def = findDef(dtd);
            if (def instanceof PrototypeDefinition pd)
                return Optional.of(pd);
        }
        return Optional.empty();
    }

    private List<PrototypeDefinition>
    findDepends(PrototypeDefinition pd) {
        var list = new ArrayList<PrototypeDefinition>(4);
        var p = pd.prototype();
        p.returnSet().flatmap(this::findPrototype).use(list::add);
        for (var v : p.parameterSet()) {
            v.type().flatmap(this::findPrototype).use(list::add);
        }
        return list;
    }

    private Optional<DAGGraph<PrototypeDefinition>>
    visitPrototype(IdentifierTable<TypeDefinition> types) {
        var all = new HashSet<PrototypeDefinition>(types.size());
        var edges = new ArrayList<Groups.G2<PrototypeDefinition,
                PrototypeDefinition>>();
        for (var def : types) {
            if (!(def instanceof PrototypeDefinition pd))
                continue;
            all.add(pd);
            for (var d : findDepends(pd)) {
                edges.add(Groups.g2(d, pd));
            }
        }
        if (all.isEmpty()) return Optional.empty();
        var dag = makeDAG(all, edges);
        dag.bfs(this::analyse);
        return Optional.of(dag);
    }

    private void visitAttribute(IdentifierTable<TypeDefinition> types) {
        for (var t : types) {
            if (t instanceof AttributeDefinition ad) analyse(ad);
        }
    }

    private Set<GlobalVariable> searchDependencies(
            IdentifierTable<GlobalVariable> all,
            Expression expr, boolean isConst) {
        var deps = new HashSet<GlobalVariable>();
        var q = new ArrayDeque<Expression>();
        q.add(expr);
        while (!q.isEmpty()) {
            var c = q.poll();
            switch (c) {
                case SymbolExpression e -> {
                    var s = e.symbol();
                    if (s.module().has())
                        return unsupported("module");

                    var ov = all.tryGet(s.name());
                    if (ov.none()) {
                        var ot = context.findType(s);
                        if (ot.match(t -> t instanceof EnumDefinition))
                            break;
                        return semantic("undeclared var: %s", s);
                    }
                    var v = ov.get();

                    if (isConst) {
                        if (v.declare() != Declare.CONST)
                            return semantic("const can't depend var: %s", e.pos());
                    } else {
                        if (v.declare() == Declare.CONST)
                            break;
                    }
                    deps.add(v);
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
                case MemberOfExpression e -> q.add(e.subject());
                case LiteralExpression e -> {
                }
                // 其他的Expression类型并不是通过解析创建的，这时不用管
                case null, default -> semantic("can't depend: %s", c);
            }
        }
        return deps;
    }

    // 创建全局变量的依赖图：便于bfs遍历分析
    private Optional<DAGGraph<GlobalVariable>>
    globalGraph(Collection<GlobalVariable> list,
                IdentifierTable<GlobalVariable> all,
                boolean isConst) {
        if (list.isEmpty()) return Optional.empty();

        var gvs = new IdentifierTable<GlobalVariable>(list.size());
        for (var gv : list) gvs.add(gv.name(), gv);

        var edges = new ArrayList<Groups.G2<
                GlobalVariable, GlobalVariable>>();
        for (var gv : list) {
            if (gv.value().none()) continue;
            var deps = searchDependencies(all,
                    gv.value().must(), isConst);
            for (var dep : deps) edges.add(Groups.g2(dep, gv));
        }
        var dag = makeDAG(gvs.values(), edges);
        return Optional.of(dag);
    }

    private Optional<DAGGraph<GlobalVariable>>
    globalVarInit(Collection<GlobalVariable> list,
                  IdentifierTable<GlobalVariable> all,
                  boolean isConst) {
        var dag = globalGraph(list, all, isConst);
        if (dag.none()) return dag;
        dag.get().bfs(this::analyse);
        return dag;
    }

    private ParseSymbolTable gst;

    public Entity analyse(Source s) {
        var tab = s.table();
        gst = tab;

        // 先分析全局常量：必须是const的基本类型才能是常量
        var constValues = tab.variables.stream()
                .filter(v -> v.declare() == Declare.CONST)
                .collect(Collectors.toSet());
        tab.dagConst = globalVarInit(constValues, tab.variables, true);

        // 再依次分析定义的类型：先分析各类型的依赖图，检查循环依赖后再按BFS分析
        // 枚举只可能依赖常量，第一个分析
        tab.enumList = visitEnum(tab.namedTypes);
        // 结构类型也只依赖常量：优先分析
        tab.dagStructures = visitStructures(tab.namedTypes);
        // 原型定义先分析：！！！这个可能有问题
        tab.dagPrototypes = visitPrototype(tab.namedTypes);
        // 先分析接口
        tab.dagInterfaces = visitInterfaces(tab.namedTypes);
        // 最后分析类
        tab.dagClasses = visitClasses(tab.namedTypes);

        // 然后分析其他全局变量
        var rest = subtract(tab.variables.values(), constValues);
        tab.dagVars = globalVarInit(rest, tab.variables, false);

        // 分析类的方法：包括方法中的语句
        tab.dagClasses.use(this::visitMethods);

        // 【未实现】属性
        visitAttribute(tab.namedTypes);

        // 分析函数：包括函数中的语句
        for (var f : tab.namedFunctions) analyse(f);

        return s;
    }

    private Entity analyse(Modifier m) {
        if (!m.attributes().isEmpty())
            unsupported("attribute");
        return m;
    }

    private Entity analyse(Refer e) {
        return e;
    }

    private Entity analyse(TypeArguments ta) {
        if (ta.isEmpty()) return ta;
        for (var a : ta) {
            analyse(a);
        }
        return ta;
    }

    private void invalid(TypeArguments ta) {
        if (ta.isEmpty()) return;
        semantic("here can't use type arguments '%s': %s", ta, ta.pos());
    }

    private void invalid(TypeParameters tp) {
        if (tp.isEmpty()) return;
        semantic("here can't use type parameters '%s'", tp);
    }

    private GenericMap genericMap(
            Entity e, TypeParameters params, TypeArguments args) {
        return genericMap(e, GenericMap.EMPTY, params, args);
    }

    private GenericMap genericMap(
            Entity e, GenericMap parent,
            TypeParameters params, TypeArguments args) {
        var size = params.size();
        if (size > args.size()) {
            return semantic("mismatch type arguments: %s", e.pos());
        }
        if (size < args.size()) {
            return semantic("too much type arguments: %s", e.pos());
        }
        if (size == 0) return parent;

        var gm = new HashMap<TypeParameter, TypeDeclarer>(size);
        for (int i = 0; i < size; i++) {
            var p = params.get(i);
            var t = args.get(i);
            if (t.maybeRefer().match(r -> r.isKind(PHANTOM))) {
                return semantic("can't use phantom-refer as type argument: %s",
                        t.pos());
            }
            gm.put(p, parent.mapIf(t));
        }
        return new GenericMap(parent, gm);
    }

    private TypeDefinition findDef(DerivedType dt) {
        var s = dt.symbol();
        var o = context.findType(s);
        if (o.has()) {
            dt.def.set(o);
            analyse(dt.generic());
            var gm = genericMap(dt, o.get().generic(), dt.generic());
            dt.gm(gm);
            return o.get();
        }

        return semantic("type %s not defined: %s",
                s, dt.pos());
    }

    private TypeDefinition findDef(DerivedTypeDeclarer dtd) {
        return findDef(dtd.derivedType());
    }

    //

    private boolean enablePhantom = false;

    private TypeDeclarer analyse(TypeDeclarer td) {
        if (enablePhantom) {
            enablePhantom = false;
        } else {
            illegalPhantom(td);
        }
        return switch (td) {
            case ArrayTypeDeclarer ee -> analyse(ee);
            case DerivedTypeDeclarer ee -> analyse(ee);
            case FuncTypeDeclarer ee -> analyse(ee);
            default -> td;
        };
    }

    private TypeDeclarer analyse(DerivedTypeDeclarer td) {
        analyse(td.generic());
        var def = findDef(td.derivedType());

        var r = td.refer();
        r.use(this::analyse);

        if (def instanceof StructureDefinition) return td;

        if (def instanceof ClassDefinition cd) {
            if (cd.resource() && r.none()) {
                return semantic("resource-class %s must be refer: %s",
                        cd.symbol(), td.pos());
            }
            return td;
        }

        if (def instanceof InterfaceDefinition) {
            if (r.has()) return td;
            return semantic("interface must be refer: %s",
                    td.pos());
        }

        if (def instanceof EnumDefinition) return td;

        if (r.none() && def instanceof PrototypeDefinition pd) {
            var ftd = new NamedFuncTypeDeclarer(td.pos(),
                    false, td.derivedType(), Lazy.of(pd));
            var pt = td.derivedType().gm().instantiate(pd.prototype());
            ftd.prototype(pt);
            return ftd;
        }

        if (r.none()) return td;
        return semantic("can't refer %s %s",
                def.domain(), def.symbol());
    }

    private void illegalPhantom(TypeDeclarer td) {
        if (!td.referKind(PHANTOM))
            return;
        semantic("can't be phantom-reference: %s",
                td.pos());
    }

    private ArrayTypeDeclarer analyse(ArrayTypeDeclarer td) {
        td.element(analyse(td.element()));
        if (td.length().has()) {
            var l = td.length().get();
            var s = calcSize(l);
            td.length(Optional.of(new LiteralExpression(l.pos(), s)));
            td.len(s.value().longValue());
        } else {
            analyse(td.refer().must());
        }
        return td;
    }

    private FuncTypeDeclarer analyse(FuncTypeDeclarer td) {
        if (td instanceof AnonFuncTypeDeclarer af) {
            analyse(af.prototype(), false);
            return td;
        }
        var ntd = (NamedFuncTypeDeclarer) td;
        var def = findDef(ntd.derivedType());
        if (!(def instanceof PrototypeDefinition pd)) {
            return semantic("'%s' is not function-prototype: %s",
                    ntd, td.pos());
        }
        ntd.def().set(pd);
        var npt = ntd.derivedType().gm().instantiate(pd.prototype());
        ntd.prototype(npt);
        return td;
    }

    private void analyse(TypeParameters e) {
        // TODO: check generic constants
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
                case SymbolExpression e -> {
                    var ov = context.findVar(e.symbol());
                    if (ov.none())
                        return semantic("var '%s' not declared: %s",
                                e.symbol(), e.symbol().pos());
                    var v = ov.get();
                    if (v.declare() != Declare.CONST)
                        return semantic("required const: %s", e.pos());
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

            var def = findDef(dtd);
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

    private Optional<DAGGraph<StructureDefinition>>
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
            analyse(sf.type());
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

    private Entity analyse(EnumDefinition def) {
        var i = 0;
        for (var v : def.values()) {
            v.val(i);
            if (v.init().none()) {
                i++;
                continue;
            }
            var il = calcInteger(v.init().must());
            v.init().set(new LiteralExpression(il.pos(), il));
            i = il.value().intValue();
            v.val(i);
            i++;
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

    private boolean checkPrototype(
            TypeDeclarer l, TypeDeclarer r, Entity e) {
        if (l instanceof FuncTypeDeclarer lfd) {
            if (r instanceof FuncTypeDeclarer rfd)
                return checkPrototype(lfd.prototype(),
                        rfd.prototype(), e) == 0;

            if (r instanceof DerivedTypeDeclarer dtd) {
                var def = dtd.def();
                if (def instanceof PrototypeDefinition pd)
                    return checkPrototype(lfd.prototype(),
                            pd.prototype(), e) == 0;
            }

        } else if (l instanceof DerivedTypeDeclarer dtd) {
            if (r instanceof FuncTypeDeclarer rfd) {
                var def = dtd.def();
                if (def instanceof PrototypeDefinition pd)
                    return checkPrototype(pd.prototype(),
                            rfd.prototype(), e) == 0;
            }
        }
        return l.equals(r);
    }

    private boolean checkParameters(
            ParameterSet l, ParameterSet r, Entity e) {
        if (l.size() != r.size()) return false;
        var size = l.size();
        for (int i = 0; i < size; i++) {
            var v = r.getVar(i);
            if (checkPrototype(l.getType(i), v.type().must(), e))
                continue;
            return false;
        }
        return true;
    }

    private int checkPrototype(
            Prototype l, Prototype r, Entity e) {
        if (!checkParameters(l.parameterSet(), r.parameterSet(), e))
            return 1;
        var lr = l.returnSet();
        var rr = r.returnSet();
        if (lr.none() != rr.none())
            return 2;
        if (lr.none()) return 0;
        // 返回值协变（Covariance）：允许“r”的返回值是“l”返回值的子类或实现类
        if (!assignable(lr.get(), rr.get(), Optional.empty(), e))
            return 2;
        return 0;
    }

    private boolean compatible(Prototype l, Prototype r, Entity e) {
        typeNest++;
        var re = checkPrototype(l, r, e);
        typeNest--;
        return switch (re) {
            case 1 -> semantic("parameters not same: %s -> %s",
                    l.pos(), r.pos());
            case 2 -> semantic("returns not compatible: %s -> %s",
                    l.pos(), r.pos());
            default -> true;
        };
    }

    // class define

    private void checkInherit(
            ClassDefinition parent, ClassDefinition child,
            IdentifierTable<ClassField> inheritFields,
            IdentifierTable<ClassMethod> inheritMethods) {
        var inherit = child.inherit().must();
        parent.children().add(child);
        if (parent.isFinal()) {
            semantic("can't inherit final %s: %s",
                    parent, inherit.pos());
            return;
        }
        // 检查同名属性
        for (var pf : parent.allFields()) {
            var cf = child.fields().tryGet(pf.name());
            if (cf.none()) {
                var nf = pf.clone();
                nf.type(inherit.gm().mapIf(nf.type()));
                inheritFields.add(nf.name(), nf);
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
                var cm = pm.declaration();
                var pp = inherit.gm().instantiate(cm.prototype());
                cm.prototype(pp);
                cm.master(child);
                inheritMethods.add(cm.name(), cm);
                continue;
            }
            var cm = o.must();
            if (!pm.generic().isEmpty() || !cm.generic().isEmpty()) {
                semantic("override method not support generic: '%s' -> '%s' %s ",
                        pm, cm, cm.pos());
                return;
            }
            if (pm.export() != cm.export()) {
                semantic("override require same export: ",
                        cm.pos());
                return;
            }
            var pp = inherit.gm().instantiate(pm.prototype());
            compatible(pp, cm.prototype(), pm);
            pm.override().add(cm);
        }

    }

    private ClassDefinition findClass(DerivedType t) {
        var dt = findDef(t);
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
        var inheritFields = new IdentifierTable<ClassField>();
        var inheritMethods = new IdentifierTable<ClassMethod>();
        if (cd.parent().has()) {
            checkInherit(cd.parent().must(), cd, inheritFields, inheritMethods);
        }
        for (var c = cd.parent(); c.has(); c = c.must().parent()) {
            cd.ancestors().add(c.must());
        }
        cd.inheritFields().addAll(inheritFields);
        cd.allFields().addAll(inheritFields);
        cd.allFields().addAll(cd.fields());
        cd.allMethods().addAll(inheritMethods);
        cd.allMethods().addAll(cd.methods());
        checkResource(cd);
    }

    private void checkImplList(
            ClassDefinition cd, InterfaceDefinition id,
            DerivedType dt) {
        for (var im : id.allMethods) {
            var o = cd.allMethods().tryGet(im.name());
            if (o.none()) {
                semantic("%s unimplement method: %s%s",
                        cd.symbol(), im.name(), im.pos());
                return;
            }
            var prot = dt.gm().instantiate(im.prototype());
            var cm = o.must();
            compatible(prot, cm.prototype(), im);
            im.override().add(cm);
        }
    }

    private void checkImplList(ClassDefinition cd) {
        if (cd.builtin()) return;
        cd.parent().use(pd ->
                cd.allImpls().addAll(pd.allImpls()));
        for (var dt : cd.impl()) {
            var td = findDef(dt);
            if ((td instanceof InterfaceDefinition id)) {
                checkImplList(cd, id, dt);
                cd.allImpls().add(id);
                id.visitParts(d -> cd.allImpls().add(d));
                continue;
            }
            semantic("require interface: %s", dt.pos());
        }
        for (var id : cd.allImpls()) {
            id.impls.add(cd);
        }
    }

    private Optional<ClassDefinition>
    getClassTypeField(TypeDeclarer t) {
        if (t instanceof DerivedTypeDeclarer ctd) {
            if (ctd.refer().has())
                return Optional.empty();

            var dt = findDef(ctd);
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
        // 先分析类的结构
        dag.bfs(this::visitClass);
        // 再分析继承关系
        dag.bfs(this::checkAllInherits);
        // 最后分析实现接口
        dag.bfs(this::checkImplList);
        // 分析方法是否修改字段：暂时没用，期望用于检查到不可变示例是否调用到了updater
        dag.bfs(cd -> new UpdaterAnalyzer().analyse(cd));
        return Optional.of(dag);
    }

    private void visitMethods(DAGGraph<ClassDefinition> dag) {
        dag.bfs(this::implMethod);
    }

    private ClassDefinition enterClass;
    private ClassMethod enterMethod;

    private void visitClass(ClassDefinition cd) {
        if (cd.builtin()) return;

        assert enterClass == null;
        enterClass = cd;

        for (var f : cd.fields()) analyse(f);

        declareMethod(cd);

        enterClass = null;
    }

    private Entity analyse(ClassField cf) {
        assert enterClass != null;
        var nt = analyse(cf.type());
        if (nt != cf.type()) cf.type(nt);
        return cf;
    }

    private void declareMethod(ClassDefinition cd) {
        for (var m : cd.methods()) {
            analyse(m.modifier());
            analyse(m.prototype(), false);
        }
    }

    private void implMethod(ClassDefinition cd) {
        assert enterClass == null;
        enterClass = cd;

        for (var m : cd.methods()) analyse(m);

        macro(cd);

        enterClass = null;
    }

    private Entity analyse(ClassMethod cm) {
        assert enterClass != null;
        assert enterMethod == null;

        enterMethod = cm;
        analyse(cm.procedure().must());
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

    private MethodExpression wrapThis(SymbolExpression e, ClassMethod m) {
        assert e.symbol().module().none();
        return new MethodExpression(e.pos(), newThis(e.pos()),
                m, e.generic());
    }

    private List<InterfaceDefinition>
    findParts(InterfaceDefinition def) {
        return def.parts().stream().map(p -> {
            var t = findDef(p);
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
            // 检查part接口的方法与继承的方法是否一致：这里返回值也要一致
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
        dag.bfs(this::analyse);
        return Optional.of(dag);
    }

    private Entity analyse(InterfaceDefinition def) {
        if (def.builtin()) return def;

        for (var m : def.methods()) analyse(m);

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

        return def;
    }

    private Entity analyse(InterfaceMethod m) {
        invalid(m.generic());
        analyse(m.prototype(), false);
        return m;
    }

    private Entity analyse(PrototypeDefinition pd) {
        analyse(pd.generic());
        analyse(pd.prototype(), false);
        return pd;
    }

    //

    private void checkMain(FunctionDefinition fd) {
        var main = FunctionDefinition.MAIN_FUNC;
        if (!main.symbol().equals(fd.symbol()))
            return;
        int r = checkPrototype(main.prototype(), fd.prototype(), fd);
        switch (r) {
            case 1 -> {
                semantic("invalid main function argument: %s", fd.pos());
                return;
            }
            case 2 -> {
                semantic("invalid main function returns: %s", fd.pos());
                return;
            }
        }
        fd.entry(true);

    }

    private FunctionDefinition enterFunc;

    private Entity analyse(FunctionDefinition fd) {
        assert enterFunc == null;
        enterFunc = fd;
        checkMain(fd);
        analyse(fd.modifier());
        analyse(fd.procedure());
        enterFunc = null;
        return fd;
    }

    private Procedure enterProc;

    private Identifier protName() {
        return enterFunc != null ? enterFunc.symbol().name()
                : enterMethod.name();
    }

    private Entity analyse(Procedure proc) {
        assert enterProc == null;
        enterProc = proc;
        context.enterScope();
        analyse(proc.prototype(), true);
        analyse(proc.body());
        checkAllPathReturn(proc);
        context.exitScope(proc);
        enterProc = null;
        return proc;
    }

    // 检查所有路径均有return
    private void checkAllPathReturn(Procedure proc) {
        if (analyzer.check(proc.body()))
            return;
        if (proc.prototype().returnSet().none()) return;

        semantic("missing return statement: %s",
                enterProc.pos());
    }

    private Entity analyse(Prototype prot, boolean addVar) {
        analyse(prot.parameterSet(), addVar);
        prot.returnSet(prot.returnSet().map(this::analyse));
        return prot;
    }

    private void analyse(ParameterSet ps, boolean addVar) {
        for (var v : ps.variables()) {
            analyse(v.modifier());
            enablePhantom = true;
            v.type().update(this::analyse);
            if (addVar) context.putVar(v);
        }
    }

    //

    private boolean enablePhantom(Refer lr, Expression e) {
        return switch (e) {
            case IndexOfExpression ee -> enablePhantom(lr, ee);
            case LiteralExpression ee -> enablePhantom(lr, ee);
            case MemberOfExpression ee -> enablePhantom(lr, ee);
            case ParenExpression ee -> enablePhantom(lr, ee.child());
            case VariableExpression ee -> enablePhantom(lr, ee.variable());
            case SymbolExpression ee -> enablePhantom(lr, ee);
            case CurrentExpression ee -> true;
            case EnumExpression ee -> false;
            default -> false;
        };
    }

    private boolean enablePhantom(Refer lr, Variable rv) {
        if (rv.type().must().maybeRefer().none()) return true;
        if (rv.declare() == Declare.CONST) return true;
        if (rv instanceof GlobalVariable) return false;
        return context.lockVar(rv);
    }

    private boolean enablePhantom(Refer lr, SymbolExpression e) {
        if (!e.generic().isEmpty()) return unsupported("generic");
        var v = context.findVar(e.symbol());
        if (v.none()) return semantic("not declared: %s", e.pos());

        return enablePhantom(lr, v.get());
    }

    private boolean enablePhantom(Refer lr, IndexOfExpression e) {
        var st = e.subject().resultType.must();
        if (!(st instanceof ArrayTypeDeclarer atd))
            return unreachable();
        var etd = atd.element();
        if (etd.maybeRefer().none())
            return enablePhantom(lr, e.subject());
        return semantic("can't convert refer-element to phantom-refer: %s",
                etd.pos());
    }

    private boolean enablePhantom(Refer lr, MemberOfExpression e) {
        var f = e.field().must();
        if (!f.enablePhantom()) return false;
        if (e.subject() instanceof EnumExpression)
            return true;

        var fr = f.type().maybeRefer();
        if (fr.none()) return enablePhantom(lr, e.subject());

        if (f.immutable())
            return enablePhantom(lr, e.subject());
        return semantic("can't convert var-refer-field to phantom-refer: %s", e.pos());
    }

    private boolean enablePhantom(Refer lr, LiteralExpression e) {
        return e.literal() instanceof StringLiteral
                || e.literal() instanceof NilLiteral;
    }

    //

    private Statement analyse(Statement s) {
        s = switch (s) {
            case DeclarationStatement ss -> analyse(ss);
            case AssignmentsStatement ss -> analyse(ss);
            case BlockStatement ss -> analyse(ss);
            case BreakStatement ss -> analyse(ss);
            case CallStatement ss -> analyse(ss);
            case ContinueStatement ss -> analyse(ss);
            case ForStatement ss -> analyse(ss);
            case GotoStatement ss -> analyse(ss);
            case IfStatement ss -> analyse(ss);
            case LabeledStatement ss -> analyse(ss);
            case ReturnStatement ss -> analyse(ss);
            case SwitchStatement ss -> analyse(ss);
            case ThrowStatement ss -> analyse(ss);
            case TryStatement ss -> analyse(ss);
            case CatchClause ss -> analyse(ss);
            case null, default -> unreachable();
        };
        return s;
    }

    private <S extends Statement> void analyse(List<S> list) {
        for (var s : list) analyse(s);
    }

    private <S extends Statement>
    void analyse(Optional<S> so) {
        so.map(this::analyse);
    }

    private Statement analyse(BlockStatement bs) {
        if (bs.newScope()) context.enterScope();
        for (var s : bs.list())
            analyse(s);
        if (bs.newScope()) context.exitScope(bs);
        return bs;
    }

    private <F extends Field> void checkInitField(
            GenericMap gm, IdentifierTable<F> fields,
            ObjectTypeDeclarer odt, ObjectExpression oe) {
        for (var f : fields) {
            var o = oe.entries().tryGet(f.name());
            if (o.none()) {
                if (!f.immutable()) continue;
                semantic("const field must init: %s",
                        oe.pos());
                return;
            }

            var v = o.get();
            var l = gm.mapIf(f.type());
            var r = odt.entries().get(f.name());
            var able = assignable(l, r, o, v);
            if (!able) {
                semantic(
                        "incompatible field '%s' and value '%s': %s",
                        f.name(), v, v.pos());
                return;
            }
        }
    }

    private boolean initializable(
            DerivedTypeDeclarer dtd, StructureDefinition sd,
            ObjectTypeDeclarer odt, ObjectExpression oe) {
        if (sd.domain() == TypeDomain.UNION) {
            if (oe.entries().size() > 1)
                return semantic("union only can init one field: %s",
                        oe.entries().getKey(1).pos());
        }
        checkInitField(dtd.gm(), sd.fields(), odt, oe);
        for (var k : oe.entries().keys()) {
            if (sd.fields().exists(k)) continue;
            return semantic("field '%s' not defined: %s", k, k.pos());
        }
        return true;
    }

    private boolean initializable(
            DerivedTypeDeclarer dtd, ClassDefinition cd,
            ObjectTypeDeclarer odt, ObjectExpression oe) {
        var fields = cd.allFields();
        checkInitField(dtd.gm(), fields, odt, oe);
        for (var k : oe.entries().keys()) {
            if (fields.exists(k)) continue;
            return semantic("unknown field '%s': %s", k, k.pos());
        }
        return true;
    }

    // 字段初始化分析
    private boolean initializable(
            DerivedTypeDeclarer l,
            ObjectTypeDeclarer o, ObjectExpression oe) {
        o.lt.set(l);

        if (oe.entries().isEmpty()) return true;
        var lt = l.def();

        if (lt instanceof StructureDefinition def)
            return initializable(l, def, o, oe);

        if (lt instanceof ClassDefinition def)
            return initializable(l, def, o, oe);

        return unreachable();
    }

    private boolean assignable(ClassDefinition l, ClassDefinition r) {
        if (r.equals(l)) return true;
        return r.parent().match(p -> assignable(l, p));
    }

    private boolean assignable(InterfaceDefinition l, ClassDefinition r) {
        if (r.impl().exists(l.symbol())) return true;

        var ok = r.parent().match(p -> assignable(l, p));
        if (ok) return true;

        for (var dt : r.impl()) {
            var id = (InterfaceDefinition) dt.def.must();
            if (assignable(l, id)) return true;
        }

        return false;
    }

    private boolean assignable(InterfaceDefinition l, InterfaceDefinition r) {
        if (l.equals(r)) return true;

        for (var dt : r.parts()) {
            var def = dt.def.must();
            if (def instanceof InterfaceDefinition id)
                if (assignable(l, id)) return true;
        }

        return false;
    }

    // 协变分析
    private boolean assignable(
            ObjectDefinition l, ObjectDefinition r) {
        if (typeNest > 0) return l.equals(r);

        if (l instanceof ClassDefinition lc) {
            if (r instanceof ClassDefinition rc) {
                return assignable(lc, rc);
            }
            return l == ClassDefinition.ObjectClass;
        }

        if (l instanceof InterfaceDefinition li) {
            if (r instanceof ClassDefinition rc) {
                return assignable(li, rc);
            }
            if (r instanceof InterfaceDefinition ri) {
                return assignable(li, ri);
            }
        }

        return unreachable();
    }

    private boolean
    assignable(DerivedTypeDeclarer l, EnumTypeDeclarer r) {
        var ld = l.def();
        return ld instanceof EnumDefinition ed && r.def().equals(ed);
    }

    private boolean
    assignable(DerivedTypeDeclarer l,
               DerivedTypeDeclarer r,
               Entity e) {
        if (!l.generic().equals(r.generic()))
            return semantic("type arguments not match '%s'%s <--> '%s'%s: %s",
                    l, l.pos(), r, r.pos(), e.pos());

        var lt = l.def();
        var rt = r.def();
        if (lt instanceof ObjectDefinition lo &&
                rt instanceof ObjectDefinition ro)
            return assignable(lo, ro);

        if (lt instanceof EnumDefinition le &&
                rt instanceof EnumDefinition re) {
            return le.equals(re);
        }
        return false;
    }

    private boolean compatible(
            DerivedTypeDeclarer l, DerivedTypeDeclarer r, Entity e) {
        return l.def() instanceof PrototypeDefinition ld &&
                r.def() instanceof PrototypeDefinition rd &&
                compatible(ld.prototype(), rd.prototype(), e);
    }

    // 不可修改引用必须单向传递
    private boolean checkImmutable(Refer lr, Refer rr) {
        if (rr.immutable() && !lr.immutable())
            return semantic("can't deliver: immutable -> mutable: %s", lr.pos());
        return true;
    }

    // 检查非空的传递
    private boolean checkRequired(TypeDeclarer l, TypeDeclarer r,
                                  Optional<Expression> re, Entity e) {
        if (!l.required()) return true;
        if (r.required()) return true;
        // 检查是否允许反向传递
        if (re.match(this::ifMarkNonNil))
            return true;

        return semantic("can't assign: optional '%s' -> required '%s', need explicitly checking: %s",
                r, l, e.pos());
    }

    // 统一检查右边值是否能被引用
    private boolean referable(Refer lr, Optional<Refer> r,
                              Optional<Expression> re) {
        if (lr.kind() == PHANTOM) {
            // 检查虚引用是否允许
            if (re.none()) return false;
            var e = re.get();
            if (enablePhantom(lr, e)) {
                // 不可修改引用必须单向传递
                if (!immutable(e, false) || lr.immutable()) return true;
                return semantic("can't convert: immutable -> mutable: %s", lr.pos());
            }
            return semantic("can't convert '%s' to phantom refer: %s",
                    e, e.pos());
        }
        assert lr.kind() == STRONG;

        if (!r.has())
            return semantic("strong-refer can't refer value: %s", lr.pos());

        var rr = r.get();
        if (rr.isKind(PHANTOM))
            return semantic("can't convert: phantom -> strong: %s", lr.pos());

        return checkImmutable(lr, rr);
    }

    private boolean assignRefer(TypeDeclarer l, LiteralExpression re) {
        re.lt.set(l);
        var lr = l.maybeRefer().must();
        if (re.literal() instanceof NilLiteral) {
            if (!lr.required()) {
                return true;
            }
            return semantic("required-refer can't set nil: %s", re.pos());
        }
        if (re.literal() instanceof StringLiteral) {
            // 字符串字面量可以传递给数组引用，当然必须是不可修改的
            return l instanceof ArrayTypeDeclarer la &&
                    la.element() instanceof PrimitiveTypeDeclarer lp
                    && lp.primitive() == Primitive.BYTE;
        }
        return false;
    }

    // 整数、浮点数、struct/union及其定长数组是可以相互转换引用的
    private boolean mappable(TypeDeclarer td, boolean mustValue) {
        if (mustValue && td.maybeRefer().has()) {
            return false;
        }
        if (td instanceof PrimitiveTypeDeclarer ptd)
            return !ptd.primitive().isBool();
        if (td instanceof DerivedTypeDeclarer dtd) {
            return dtd.def() instanceof StructureDefinition;
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
            if (dtd.def() instanceof StructureDefinition sd)
                return sd.layout().must().size();
        }

        return semantic("unsupported sizeof(%s): %s", td, td.pos());
    }

    private long estimateSize(TypeDeclarer td) {
        if (!(td instanceof ArrayTypeDeclarer atd))
            return typeSize(td);

        if (atd.refer().has())
            return semantic("can't get size of %s: %s", td, td.pos());

        var es = estimateSize(atd.element());
        return atd.len() * es;
    }

    // mappable类型需检查边界：编译时检查，或运行时检查
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

    private boolean assignRefer(
            TypeDeclarer l, TypeDeclarer r,
            Optional<Expression> re, Entity e) {
        var lRef = l.maybeRefer();
        assert lRef.has();

        if (r instanceof LiteralTypeDeclarer)
            return assignRefer(l, (LiteralExpression) re.must());

        if (!referable(lRef.get(), r.maybeRefer(), re))
            return false;

        if (mappable(l, false) &&
                mappable(r, false)) {
            if (checkBounds(l, r))
                return true;

            return semantic("out of bounds: convert %s(size=%d):%s to %s(size=%d):%s",
                    l, l.size(), l.pos(), r, r.size(), r.pos());
        }

        if (r instanceof PrimitiveTypeDeclarer rp) {
            return l instanceof PrimitiveTypeDeclarer lp &&
                    rp.primitive() == lp.primitive();
        }

        if (r instanceof EnumTypeDeclarer rd) {
            if (l instanceof DerivedTypeDeclarer ld)
                return assignable(ld, rd);
        }

        // Objects
        if (r instanceof DerivedTypeDeclarer rd) {
            if (l instanceof DerivedTypeDeclarer ld)
                return assignable(ld, rd, e);
        }

        // Arrays
        if (r instanceof ArrayTypeDeclarer ra) {
            // 数组比较复杂，虚比较每一层的元素
            // 数组引用本身可以是虚引用，但元素不能是，通过typeNest控制
            if (l instanceof ArrayTypeDeclarer la) {
                typeNest += 2;
                try {
                    if (la.element().equals(ra.element())) {
                        return true;
                    }
                    if (la.element() instanceof DerivedTypeDeclarer ldt &&
                            ra.element() instanceof DerivedTypeDeclarer rdt) {
                        return assignable(ldt, rdt, e);
                    }
                } finally {
                    typeNest -= 2;
                }
            }
        }

        return semantic("'%s' can't refer '%s': %s", l, r, l.pos());
    }

    private boolean assignValue(TypeDeclarer l, LiteralExpression re) {
        assert l.maybeRefer().none();
        re.lt.set(l);

        if (re.literal() instanceof IntegerLiteral)
            return l instanceof PrimitiveTypeDeclarer ptd
                    && ptd.primitive().isInteger();

        if (re.literal() instanceof FloatLiteral)
            return l instanceof PrimitiveTypeDeclarer ptd
                    && ptd.primitive().isFloat();

        if (re.literal() instanceof BoolLiteral)
            return l instanceof PrimitiveTypeDeclarer ptd
                    && ptd.primitive().isBool();

        if (re.literal() instanceof NilLiteral) {
            if (l instanceof FuncTypeDeclarer ftd) {
                if (!ftd.required()) return true;
                return semantic("required '%s' can't set nil: %s",
                        l, l.pos());
            }
        }

        if (re.literal() instanceof StringLiteral sl) {
            // 字符串字面量只能传递给[N]byte类型
            if (!(l instanceof ArrayTypeDeclarer la))
                return false;
            if (la.refer().has()) return false;
            if (!(la.element() instanceof PrimitiveTypeDeclarer lp))
                return false;
            if (lp.primitive() != Primitive.BYTE) return false;
            if (la.len() >= sl.length()) return true;
            return semantic("string data overflow: %s", sl.pos());
        }

        return false;
    }

    private boolean assignValue(
            ArrayTypeDeclarer l, ArrayTypeDeclarer r,
            Optional<Expression> re, Entity e) {
        assert l.refer().none();

        if (r.refer().has()) return false; // Not: [value] = [refer]

        if (re.has()) {
            if (re.must() instanceof ArrayExpression ae) {
                ae.lt.set(l);
                // 数组初始化分析
                var i = 0;
                for (var ev : ae.elements()) {
                    if (assignable(l.element(), r.element(),
                            Optional.of(ev), e)) {
                        i++;
                        continue;
                    }
                    return semantic(
                            "array element at %d not match type %s: %s",
                            i, l.element(), ev.pos());
                }
            } else {
                // 分析元素
                if (!assignable(l.element(), r.element(),
                        Optional.empty(), e))
                    return false;
            }
        } else {
            var elOk = assignable(l.element(), r.element(),
                    Optional.empty(), e);
            if (!elOk) return false;
        }

        if (re.match(v -> v instanceof ArrayExpression)) {
            if (r.element() instanceof VoidTypeDeclarer) return true;
            if (!l.literal() && l.len() < r.len()) {
                return semantic("index out of bound: %s", r.pos());
            }
            return true;
        }
        return l.len().longValue() == r.len().longValue();
    }

    private boolean assignValue(
            TypeDeclarer l, TypeDeclarer r,
            Optional<Expression> re, Entity e) {
        assert l.maybeRefer().none();

        if (r instanceof LiteralTypeDeclarer)
            return assignValue(l, (LiteralExpression) re.must());

        if (r instanceof ObjectTypeDeclarer ro) {
            if (l instanceof DerivedTypeDeclarer ld)
                return initializable(ld, ro,
                        (ObjectExpression) re.must());
            if (l instanceof ObjectTypeDeclarer lo) {
                var oe = (ObjectExpression) re.must();
                if (lo.dtd().has()) {
                    if (ro.dtd().has())
                        return lo.dtd().equals(ro.dtd());
                    return initializable(lo.dtd().get(), ro, oe);
                } else {
                    if (ro.dtd().none()) return true;
                    return initializable(ro.dtd().get(), lo, oe);
                }
            }
        }

        if (r instanceof ArrayTypeDeclarer ra) {
            // 数组比较复杂，虚比较每一层的元素
            // 数组引用本身可以是虚引用，但元素不能是，通过typeNest控制
            if (l instanceof ArrayTypeDeclarer la) {
                var d = ra.literal() ? 0 : 2;
                typeNest += d;
                try {
                    return assignValue(la, ra, re, e);
                } finally {
                    typeNest -= d;
                }
            }
        }

        if (r instanceof FuncTypeDeclarer rf) {
            if (l instanceof FuncTypeDeclarer lf)
                // TODO: 检查空
                return compatible(lf.prototype(),
                        rf.prototype(), e);
        }

        if (r instanceof DerivedTypeDeclarer rd) {
            if (l instanceof DerivedTypeDeclarer ld) {
                var eq = ld.derivedType().equals(rd.derivedType()) &&
                        (rd.refer().none() || re.match(v ->
                                v instanceof CurrentExpression));
                if (eq) return true;
                if (compatible(ld, rd, e))
                    return true;
            }
            if (l instanceof ObjectTypeDeclarer lo) {
                if (re.must() instanceof ObjectExpression oe)
                    return initializable(rd, lo, oe);
            }
            if (l instanceof EnumTypeDeclarer ld)
                return ld.def().equals(rd.def());
        }

        if (r instanceof EnumTypeDeclarer rd) {
            if (l instanceof DerivedTypeDeclarer ld) {
                return rd.def().equals(ld.def());
            }
        }

        return false;
    }

    // 赋值的类型检查入口
    private boolean assignable(
            TypeDeclarer l, TypeDeclarer r,
            Optional<Expression> re, Entity e) {
        if (l.equals(r)) return true;

        if (!checkRequired(l, r, re, e))
            return false;

        var lr = l.maybeRefer();
        if (lr.none()) return assignValue(l, r, re, e);

        return assignRefer(l, r, re, e);
    }

    private int typeNest = -1;

    private void unmarkNonNilVar(Assignment a) {
        if (!(a.operand() instanceof VariableOperand vo)) return;
        var v = vo.variable().must();
        var t = v.type().must();
        if (t.required()) return;

        if (a.value() instanceof LiteralExpression le
                && le.literal() instanceof NilLiteral) {
            ifUnmarkNonNil(v);
            return;
        }

        var vt = a.value().resultType.must();
        if (vt.required()) return;
        ifUnmarkNonNil(v);
    }

    private void analyse(Assignment a) {
        var og = optimize(a.operand());
        var vg = optimize(a.value());
        if (vg.b() instanceof VoidTypeDeclarer) {
            semantic("%s used as a value, but it returns nothing: %s",
                    a.value(), a.value().pos());
            return;
        }
        if (assignable(og.b(), vg.b(), Optional.of(vg.a()), a)) {
            a.operand(og.a());
            a.value(vg.a());
            unmarkNonNilVar(a);
            return;
        }
        semantic("incompatible %s = %s: %s",
                a.operand(), a.value(), a.pos());
    }

    private Statement analyse(AssignmentsStatement as) {
        for (var a : as.list())
            analyse(a);
        return as;
    }

    private TypeDeclarer fromLiteral(TypeDeclarer td, boolean enablePhantom) {
        if (td instanceof LiteralTypeDeclarer ltd) {
            var p = ltd.literal().compatible();
            if (p.has())
                return p.must().declarer(td.pos());
            if (ltd.literal() instanceof StringLiteral sl) {
                return sl.array(Optional.of(enablePhantom ? PHANTOM : STRONG));
            }
            return semantic("auto type-infer can't support %s: %s",
                    td, td.pos());
        } else if (td instanceof ArrayTypeDeclarer atd) {
            var ntd = fromLiteral(atd.element(), false);
            var na = new ArrayTypeDeclarer(atd.pos(), ntd,
                    atd.length(), atd.refer(), atd.literal());
            na.len(atd.len());
            na.unit(atd.unit());
            analyse(na);
            return na;
        } else if (td instanceof ObjectTypeDeclarer ||
                td instanceof VoidTypeDeclarer) {
            return semantic("can't infer type from %s: %s",
                    td, td.pos());
        }
        return td;
    }

    private TypeDeclarer literal2Std(TypeDeclarer t) {
        if (t instanceof ObjectTypeDeclarer ||
                t instanceof LiteralTypeDeclarer ||
                (t instanceof ArrayTypeDeclarer at
                        && at.literal())) {
            return fromLiteral(t, true);
        }
        return t;
    }

    private void initVar(Variable v) {
        var e = v.value().must();
        var g = optimize(e);
        v.value().set(g.a());
        var t = g.b();
        if (t instanceof VoidTypeDeclarer) {
            semantic("%s used as a value, but it returns nothing: %s",
                    e, e.pos());
            return;
        }
        if (t.isNil() && v.declare() == Declare.CONST) {
            semantic("let a const = nil is meaningless: %s",
                    e, e.pos());
            return;
        }
        if (v.type().has()) {
            var l = v.type().must();
            // check type
            if (assignable(l, t, Optional.of(g.a()), v)) {
                return;
            }
            semantic("incompatible, can't use '%s' as '%s': %s",
                    t, l, v.pos());
        } else {
            v.type().set(literal2Std(t));
        }
    }

    private void analyse(Variable v) {
        analyse(v.modifier());
        v.type().update(t -> {
            enablePhantom = true;
            return analyse(t);
        });
        if (v.value().none()) {
            if (v.declare() == Declare.CONST) {
                semantic("const must init: %s", v.pos());
            }
            if (v.type().match(TypeDeclarer::required)) {
                semantic("required refer must be init: %s", v.pos());
            }
        } else {
            initVar(v);
        }
    }

    private Statement analyse(DeclarationStatement ds) {
        for (var v : ds.variables()) {
            analyse(v);
            context.putVar(v);
        }
        return ds;
    }

    private Statement analyse(CallStatement e) {
        e.call().stmt().set(e);
        var g = optimize((Expression) e.call());
        if (g.a() instanceof CallExpression ce) {
            e.call(ce);
            if (!needRelay(g.b())) return e;

            var ve = makeRelay(ce);
            var ds = new DeclarationStatement(e.pos(), List.of(ve.variable()));
            var bs = new BlockStatement(e.pos(), List.of(ds));
            e.replace().set(bs);
            bs.stack(ds.variables());
            return e;
        }

        var be = (BlockExpression) g.a();
        var bs = new BlockStatement(e.pos(), be.block());
        e.replace().set(bs);
        var ds = (DeclarationStatement) be.block().getFirst();
        if (needRelay(g.b())) {
            bs.stack(ds.variables());
        } else {
            var v = ds.variables().removeLast();
            var ce = (CallExpression) v.value().must();
            var cs = new CallStatement(e.pos(), ce);
            bs.list().add(cs);
            bs.stack(ds.variables());
        }
        return e;
    }

    private Statement analyse(LabeledStatement e) {
        return analyse(e.target());
    }

    private final Stack<ForStatement> loopStack = new Stack<>();

    private LabeledStatement checkLabel(Identifier label) {
        assert enterProc != null;
        var s = enterProc.labels().get(label);
        if (s != null) return s;
        return semantic("Use of undeclared label '%s': %s", label, label.pos());
    }

    private Statement analyse(BreakStatement e) {
        if (loopStack.isEmpty())
            return semantic("break of loop: %s", e.pos());

        if (e.label().none()) {
            e.target.set(loopStack.peek());
            return e;
        }

        var ls = checkLabel(e.label().get());
        if (ls.target() instanceof ForStatement fs) {
            e.target.set(fs);
            return e;
        }
        return semantic("break label '%s' is marked for-loop: %s", e.pos());
    }

    private Statement analyse(ContinueStatement e) {
        if (loopStack.isEmpty())
            return semantic("continue out of loop: %s", e.pos());

        if (e.label().none()) {
            e.target.set(loopStack.peek());
            return e;
        }

        var ls = checkLabel(e.label().get());
        if (ls.target() instanceof ForStatement fs) {
            e.target.set(fs);
            return e;
        }
        return semantic("continue label '%s' is marked for-loop: %s", e.pos());
    }

    private Statement analyse(ForStatement e) {
        loopStack.push(e);
        context.enterScope();
        if (e instanceof ConditionalForStatement ce) {
            analyse(ce);
            ifCheckNonNil(ce.condition(), true);
            analyse(e.body());
            ifExitNonNilScope(e.body());
        } else if (e instanceof IterableForStatement ie) {
            analyse(ie);
            analyse(e.body());
        }
        context.exitScope(e);
        loopStack.pop();
        return e;
    }

    private ForStatement analyse(ConditionalForStatement e) {
        analyse(e.initializer());
        var cg = optimize(e.condition());
        e.condition(cg.a());
        if (!cg.b().isBool() || cg.b().maybeRefer().has())
            return semantic("condition must be bool-value: %s",
                    e.condition().pos());

        if (cg.a() instanceof LiteralExpression le)
            e.cond().set((BoolLiteral) le.literal());

        analyse(e.updater());
        return e;
    }

    private ConditionalForStatement forReplace(
            Identifier in, Identifier vn, TypeDeclarer vt,
            Function<Expression, Expression> vec,
            PrimaryExpression size, Statement body) {
        // create index variable
        var it = Primitive.INT.declarer(in.pos());
        var dv = new IntegerLiteral(in.pos(), 0).expr();
        var iv = new Variable(in.pos(), Modifier.empty(), Declare.CONST, in,
                Lazy.of(it), Lazy.of(dv));
        context.putVar(iv);

        // create value variable
        var ie = new VariableExpression(in.pos(), iv);
        var ve = vec.apply(ie);
        ve.resultType.set(vt);
        var vv = new Variable(vn.pos(), Modifier.empty(), Declare.CONST, vn,
                Lazy.of(vt), Lazy.of(ve));
        context.putVar(vv);

        // make a ConditionalForStatement for replace
        var init = new DeclarationStatement(ZERO, List.of(iv));
        size.resultType.set(it);
        var cond = new BinaryExpression(ZERO, BinaryOperator.LT, ie, size);
        cond.resultType.set(Primitive.BOOL.declarer(ZERO));

        var ue = new BinaryExpression(ZERO, BinaryOperator.ADD, ie,
                new IntegerLiteral(ZERO, 1).expr());
        ue.resultType.set(it);
        var oi = new VariableOperand(ZERO, new Symbol(in));
        oi.variable().set(iv);
        oi.type.set(it);
        var ui = new Assignment(ZERO, oi, ue);
        var ov = new VariableOperand(ZERO, new Symbol(vn));
        ov.variable().set(vv);
        ov.type.set(vt);
        var update = new AssignmentsStatement(ZERO, List.of(ui));


        var ds = new DeclarationStatement(ZERO, List.of(vv));
        var rb = new BlockStatement(ZERO, List.of(ds, body));
        return new ConditionalForStatement(ZERO, rb,
                Optional.of(init), cond, Optional.of(update));
    }

    private ForStatement forIterable(
            IterableForStatement e, ArrayTypeDeclarer atd) {
        var args = e.arguments();
        if (args.size() > 2)
            return semantic("can't over 2 receivers: %s",
                    args.get(2).pos());

        var in = args.size() > 1 ? args.getLast() :
                new Identifier(ZERO, "feng$index", true);
        var vn = args.getFirst();
        var size = atd.refer().none() ?
                new IntegerLiteral(ZERO, atd.len()).expr() :
                new ArrayLenExpression(ZERO, e.iterable(), atd);

        var rp = forReplace(in, vn, atd.element(), ie ->
                        new IndexOfExpression(vn.pos(), e.iterable(), ie),
                size, e.body());
        e.replace.set(rp);
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
        var dt = new DerivedType(a.pos(), ed.symbol(), TypeArguments.EMPTY);
        dt.def.set(ed);
        var t = new EnumTypeDeclarer(a.pos(), ed);

        var size = new IntegerLiteral(ZERO, ed.values().size()).expr();
        var rp = forReplace(
                new Identifier(ZERO, "feng$id", true), a, t, ie ->
                        new EnumIdExpression(ZERO, ed, ie), size, e.body());
        e.replace.set(rp);
        return e;
    }

    private ForStatement analyse(IterableForStatement e) {
        var g = optimize(e.iterable());
        e.iterable((PrimaryExpression) g.a());
        if (g.b() instanceof ArrayTypeDeclarer atd)
            return forIterable(e, atd);

        if (g.b() instanceof DefinitionDeclarer dtd)
            return forIterable(e, dtd);

        return semantic("no iterable implement %s: %s",
                g.b(), e.iterable().pos());

    }

    private Statement analyse(GotoStatement e) {
        e.target.set(checkLabel(e.label()).label());
        return e;
    }

    private void ifCheckNonNil(
            Expression e, Set<Variable> vars, boolean yes) {
        if (e instanceof CheckNilExpression cne) {
            if (cne.subject() instanceof VariableExpression ve) {
                if (yes != cne.nil())
                    vars.add(ve.variable());
            }
        } else if (e instanceof BinaryExpression ve) {
            if (ve.operator() == BinaryOperator.AND) {
                ifCheckNonNil(ve.left(), vars, yes);
                ifCheckNonNil(ve.right(), vars, yes);
            }
        } else if (e instanceof ParenExpression pe) {
            ifCheckNonNil(pe.child(), vars, yes);
        }
    }

    private static class NonNilCheckSet {
        private final Set<Variable> checked = new HashSet<>();
    }

    // 变量显式判断非空之后将其标记，作为反向传递依据
    private final Stack<NonNilCheckSet> ifNonNilVars = new Stack<>();

    private void ifCheckNonNil(Expression e, boolean yes) {
        var set = new NonNilCheckSet();
        ifCheckNonNil(e, set.checked, yes);
        ifNonNilVars.push(set);
    }

    private void ifExitNonNilScope(Statement s) {
        ifNonNilVars.pop();
    }

    private void ifUnmarkNonNil(Variable v) {
        for (NonNilCheckSet set : ifNonNilVars) {
            set.checked.remove(v);
        }
    }

    private boolean ifMarkNonNil(Expression e) {
        if (e instanceof VariableExpression ve)
            return ifMarkNonNil(ve.variable());

        if (e instanceof ParenExpression ve)
            return ifMarkNonNil(ve.child());

        return false;
    }

    private boolean ifMarkNonNil(Variable v) {
        for (var set : ifNonNilVars) {
            if (set.checked.contains(v)) return true;
        }
        return false;
    }

    private Statement analyse(IfStatement e) {
        context.enterScope();
        analyse(e.init());

        var g = optimize(e.condition());
        if (!g.b().isBool()) {
            return semantic("if condition must be bool: %s",
                    e.condition().pos());
        }
        e.condition(g.a());
        if (g.a() instanceof LiteralExpression le)
            e.cond().set((BoolLiteral) le.literal());

        ifCheckNonNil(g.a(), true);
        analyse(e.yes());
        ifExitNonNilScope(e.yes());
        e.not().use(not -> {
            ifCheckNonNil(g.a(), false);
            analyse(not);
            ifExitNonNilScope(not);
        });

        context.exitScope(e);
        return e;
    }

    private Statement analyse(ReturnStatement e) {
        assert enterProc != null;
        e.procedure().set(enterProc);
        e.local(context.local().toList()); // 收集已声明变量，方便释放
        var prot = enterProc.prototype();
        var pr = prot.returnSet();
        if (pr.none()) {
            if (e.result().none()) return e;
            return semantic("'%s' has no return value: %s",
                    protName(), e.pos());
        }
        if (e.result().none())
            return semantic("'%s' has return value: %s",
                    protName(), e.pos());

        var er = e.result().get();
        var g = optimize(er);
        e.result(Optional.of(g.a()));
        if (assignable(pr.get(), g.b(), e.result(), e)) return e;
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
                if (c instanceof SymbolExpression le) {
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

    private Statement analyse(SwitchStatement e) {
        context.enterScope();
        if (e.init().has()) analyse(e.init().get());

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
            if (dtd.def() instanceof EnumDefinition ed) {
                checkConstantEnum(e, ed);
                ok = true;
            }
        }
        if (!ok) {
            return semantic("value require integer or enum: %s",
                    cv.pos());
        }

        for (var br : e.branches()) analyse(br.body());

        e.defaultBranch().use(br -> analyse(br.body()));

        context.exitScope(e);
        return e;
    }

    private Statement analyse(ThrowStatement e) {
        assert enterProc != null;
        e.procedure().set(enterProc);
        e.local(context.local().toList()); // 收集已声明变量，方便释放
        var g = optimize(e.exception());
        e.exception(g.a());
        return e;
    }

    private Statement analyse(TryStatement e) {
        analyse(e.body());
        analyse(e.catchClauses());
        analyse(e.finallyClause());
        return e;
    }

    private Statement analyse(CatchClause e) {
        context.enterScope();
        var v = e.argument();
        analyse(v.modifier());
        if (e.typeSet().size() > 1) {
            // TODO: 推导类型的并集
            return unsupported("catch multi types: %s",
                    e.typeSet().get(1).pos());
        } else {
            v.type().set(e.typeSet().getFirst());
        }
        for (var td : e.typeSet()) analyse(td);
        context.putVar(v);
        analyse(e.body());
        context.exitScope(e);
        return e;
    }


    //

    private boolean immutable(IndexOfExpression e, boolean left) {
        var t = e.resultType.must();
        var r = t.maybeRefer();
        if (r.has()) return r.get().immutable();
        return immutable(e.subject(), left);
    }

    private boolean immutable(MemberOfExpression e, boolean left) {
        var r = e.resultType.must().maybeRefer();
        if (r.has()) return r.get().immutable();
        if (e.field().must() instanceof ClassField cf) {
            if (cf.declare() == Declare.CONST) return true;
        }
        return immutable(e.subject(), left);
    }

    private boolean immutable(BlockExpression e, boolean left) {
        if (e.origin().has()) {
            var o = e.origin().must();
            return immutable(o, left);
        }
        var t = e.resultType.must();
        var ref = t.maybeRefer();
        return ref.none() || ref.get().immutable();
    }

    private boolean immutable(CallExpression e) {
        var t = e.resultType.must();
        var ref = t.maybeRefer();
        return ref.none() || ref.get().immutable();
    }

    private boolean immutable(Variable v) {
        var ref = v.type().must().maybeRefer();
        if (ref.none())
            return v.declare() == Declare.CONST;
        return ref.get().immutable();
    }

    private boolean immutable(SymbolExpression e) {
        var o = context.findVar(e.symbol());
        if (o.none()) return semantic("var '%s' not declared", e.symbol());
        return immutable(o.get());
    }

    private boolean immutable(AssertExpression e, boolean left) {
        var im = e.type().maybeRefer().match(Refer::immutable);
        return im || immutable(e.subject(), left);
    }

    // 检查不可修改值的入口
    private boolean immutable(Expression e, boolean left) {
        return switch (e) {
            case CallExpression ee -> immutable(ee);
            case IndexOfExpression ee -> immutable(ee, left);
            case MemberOfExpression ee -> immutable(ee, left);
            case ParenExpression ee -> immutable(ee.child(), left);
            case SymbolExpression ee -> immutable(ee);
            case VariableExpression ee -> immutable(ee.variable());
            case AssertExpression ee -> immutable(ee, left);
            case BlockExpression ee -> immutable(ee, left);
            case MethodExpression ee -> true;
            case NewExpression ee -> false;
            case CurrentExpression ee -> false;
            case DereferExpression ee -> left;
            case ObjectExpression ee -> left;
            case ArrayExpression ee -> left;
            case SizeofExpression ee -> left;
            case LiteralExpression ee -> left;
            case CheckNilExpression ee -> left;
            case ReferEqualExpression ee -> left;
            case BinaryExpression ee -> left;
            case UnaryExpression ee -> left;
            case EnumExpression ee -> true;
            case null, default -> unreachable();
        };
    }

    // 赋值左操作数分析入口
    private Groups.G2<Operand, TypeDeclarer> optimize(Operand o) {
        Groups.G2<Operand, TypeDeclarer> g = switch (o) {
            case IndexOperand io -> optimize(io);
            case FieldOperand fo -> optimize(fo);
            case VariableOperand vo -> optimize(vo);
            case DereferOperand op -> optimize(op);
            case null, default -> unreachable();
        };
        g.a().type.set(g.b());
        return g;
    }

    private Groups.G2<Operand, TypeDeclarer> optimize(IndexOperand op) {
        var ig = optimize(op.index());
        if (ig.b() instanceof PrimitiveTypeDeclarer ptd) {
            if (!ptd.primitive().isInteger()) {
                return semantic("index require integer: %s", op.index().pos());
            }
        } else if (ig.b() instanceof LiteralTypeDeclarer ltd) {
            if (!ltd.isInteger()) {
                return semantic("index require integer: %s", op.index().pos());
            }
        }

        var sg = optimize(op.subject());
        if (!(sg.b() instanceof ArrayTypeDeclarer td)) {
            return semantic("require array: %s", op.pos());
        }

        var r = td.refer();
        if (r.has()) {
            if (r.get().immutable())
                return semantic("immutable array '%s': %s", op, op.pos());
        } else {
            if (immutable(sg.a(), true))
                return semantic("immutable array '%s': %s", op, op.pos());
        }

        var s = (PrimaryExpression) sg.a();
        var n = wrapRelayOperand(s, _s -> {
            return new IndexOperand(op.pos(), _s, ig.a());
        });
        return Groups.g2(n, td.element());
    }

    private Groups.G2<Operand, TypeDeclarer> optimize(FieldOperand op) {
        var name = op.field();

        var sg = optimize(op.subject());
        var td = sg.b();
        if (td instanceof VoidTypeDeclarer) return unreachable();

        if (!(td instanceof DerivedTypeDeclarer dtd))
            return semantic("illegal operand %s: %s", name, op.pos());

        var def = dtd.def();
        Optional<? extends Field> of = switch (def) {
            case StructureDefinition sd -> sd.fields().tryGet(name);
            case ClassDefinition cd -> cd.allFields().tryGet(name);
            case null, default -> semantic("%s have no any field: %s",
                    def, op.pos());
        };

        if (of.none())
            return semantic("field %s not defined: %s", name, name.pos());
        var f = of.get();
        if (f instanceof ClassField cf && cf.declare() == Declare.CONST)
            return semantic("immutable field: %s", name.pos());

        var r = td.maybeRefer();
        if (r.has()) {
            if (r.get().immutable())
                return semantic("immutable operand '%s': %s", op, op.pos());
        } else {
            if (immutable(sg.a(), true))
                return semantic("immutable operand '%s': %s", op, op.pos());
        }

        var s = (PrimaryExpression) sg.a();
        var n = wrapRelayOperand(s, _s -> {
            return new FieldOperand(op.pos(), _s, name);
        });
        var t = dtd.gm().mapIf(f.type());
        return Groups.g2(n, t);
    }

    private Groups.G2<Operand, TypeDeclarer> optimize(VariableOperand op) {
        var s = op.symbol();
        var o = context.findVar(s);
        if (o.has()) {
            var v = o.get();
            if (v.declare() == Declare.CONST)
                return semantic("immutable operand %s: %s", s, s.pos());
            if (context.isVarLocked(v))
                return semantic("readonly operand %s: %s", s, s.pos());

            op.variable().set(o);
            return Groups.g2(op, v.type().must());
        }

        if (enterClass != null && s.module().none()) {
            var f = enterClass.allFields().tryGet(s.name());
            if (f.has()) {
                if (f.get().declare() == Declare.CONST)
                    return semantic("immutable operand %s: %s", s, s.pos());
                // 操作数加上`this.`前缀，方便后续处理
                var n = new FieldOperand(op.pos(), newThis(s.pos()), s.name());
                return Groups.g2(n, f.get().type());
            }
        }
        return semantic("undefined operand %s: %s", s, s.pos());
    }

    private Groups.G2<Operand, TypeDeclarer> optimize(DereferOperand op) {
        var g = optimize(op.subject());
        dereferable(g.b());
        var ref = g.b().maybeRefer().must();
        if (ref.immutable())
            return semantic("immutable refer '%s': %s", op, op.pos());

        var s = (PrimaryExpression) g.a();
        var n = wrapRelayOperand(s, _s -> {
            return new DereferOperand(op.pos(), _s);
        });
        var t = g.b().derefer().must();
        return Groups.g2(n, t);
    }


    //
    // 表达式-expression
    // 推导类型、分析语义、计算常量、按需重构
    //

    // 表达式分析同一入口
    private Groups.G2<Expression, TypeDeclarer> optimize(Expression e) {
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
            case SymbolExpression ee -> optimize(ee);
            case BlockExpression ee -> optimize(ee);
            case VariableExpression ee -> Groups.g2(ee,
                    ee.variable().type().must());
            case DereferExpression ee -> optimize(ee);
            case null, default -> unreachable();
        };
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
            var old = gst.stringCache.putIfAbsent(lit, lit);
            if (old != null) lit = old;
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

    private boolean referCompare(
            TypeDeclarer l, TypeDeclarer r, Entity e) {
        if (mappable(l) && mappable(r)) {
            return true; // primitive, structure, and it's array
        }
        if (l instanceof DerivedTypeDeclarer ld &&
                r instanceof DerivedTypeDeclarer rd) {
            return objectBinOp(ld, rd);
        }
        if (l instanceof ArrayTypeDeclarer la &&
                r instanceof ArrayTypeDeclarer ra) {
            return assignable(la.element(), ra.element(),
                    Optional.empty(), e);
        }
        return false;
    }

    private Optional<TypeDeclarer> referBinOp(
            TypeDeclarer l, TypeDeclarer r, BinaryExpression e) {
        Optional<TypeDeclarer> ret = Optional.of(
                Primitive.BOOL.declarer(e.pos()));

        var lr = l.maybeRefer();
        var rr = r.maybeRefer();
        if (lr.none() && rr.none()) // value type, directly compare
            return l.equals(r) ? ret : Optional.empty();

        if (lr.has() || rr.has()) {
            if (referCompare(l, r, e))
                return ret;
        }

        return Optional.empty();
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

    // 转换表达式为check-nil，方便后续处理
    private Groups.G2<Expression, TypeDeclarer> checkNilBinOp(
            Groups.G2<Expression, TypeDeclarer> g, BinaryExpression e) {
        if (e.operator() != BinaryOperator.EQ &&
                e.operator() != BinaryOperator.NE)
            return semantic("nil can't %s a reference: %s",
                    e.operator(), e.pos());

        boolean nil = e.operator() == BinaryOperator.EQ;
        var t = new PrimitiveTypeDeclarer(e.pos(),
                Primitive.BOOL, Optional.empty());

        if (g.b().required()) {
            // required refer cannot be empty, so direct set to literal-bool
            var n = new LiteralExpression(e.pos(),
                    new BoolLiteral(e.pos(), nil));
            return Groups.g2(n, t);
        }

        if (!(g.b() instanceof FuncTypeDeclarer)
                && g.b().maybeRefer().none())
            return semantic("'%s' can't check-nil: %s", g.a(), e.pos());

        var s = (PrimaryExpression) g.a();
        var n = wrapRelayExpr(s, t, a -> {
            return new CheckNilExpression(e.pos(), a, nil);
        });
        return Groups.g2(n, t);
    }

    private boolean objectBinOp(
            DerivedTypeDeclarer l, DerivedTypeDeclarer r) {
        if (l.def() instanceof ObjectDefinition lo &&
                r.def() instanceof ObjectDefinition ro)
            return assignable(lo, ro) ||
                    assignable(ro, lo);
        return false;
    }

    private Optional<TypeDeclarer> derivedTypeBinOp(
            TypeDeclarer l, TypeDeclarer r, BinaryExpression e) {
        if (e.operator() != BinaryOperator.EQ &&
                e.operator() != BinaryOperator.NE)
            return Optional.empty();

        if (assignable(l, r, Optional.empty(), e))
            return Optional.of(Primitive.BOOL.declarer(e.pos()));

        return Optional.empty();
    }


    private Groups.G2<Expression, TypeDeclarer>
    optimize(BinaryExpression e) {
        var l = optimize(e.left());
        var r = optimize(e.right());
        if (l.a() instanceof LiteralExpression le &&
                r.a() instanceof LiteralExpression re) {
            return optimize(e, le, re);
        }

        if (l.b().isNil()) return checkNilBinOp(r, e);
        if (r.b().isNil()) return checkNilBinOp(l, e);

        var lr = l.b().maybeRefer();
        var rr = r.b().maybeRefer();
        if (lr.none() && rr.none()) {
            var n = new BinaryExpression(e.pos(), e.operator(), l.a(), r.a());
            var t = primitiveBinOp(l.b(), r.b(), e);
            if (t.none()) t = derivedTypeBinOp(l.b(), r.b(), e);
            if (t.has()) return Groups.g2(n, t.get());

            return semantic("not support %s %s %s: %s",
                    l.b(), e.operator(), r.b(), e.pos());
        }

        var isEqOp = BinaryOperator.SetEquals.contains(e.operator());
        if (isEqOp && referCompare(l.b(), r.b(), e)) {
            var t = Primitive.BOOL.declarer(e.pos());
            var x = (PrimaryExpression) l.a();
            var y = (PrimaryExpression) r.a();
            var n = wrapRelayExpr(x, y, t, (_x, _y) -> {
                return new ReferEqualExpression(e.pos(),
                        (PrimaryExpression) _x,
                        (PrimaryExpression) _y,
                        e.operator() == BinaryOperator.EQ);
            });
            return Groups.g2(n, t);
        }
        return semantic("not support %s %s %s: %s",
                l.b(), e.operator(), r.b(), e.pos());

    }

    private Groups.G2<Expression, TypeDeclarer>
    optimizeLit(UnaryExpression e, LiteralExpression le) {
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

        return semantic("%s not support operate %s: %s",
                l.type(), op, l.pos());
    }

    private boolean checkUnaryOperate(UnaryOperator op, TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            var p = ptd.primitive();
            if (p.isInteger()) return true;
            if (p.isFloat()) return op != UnaryOperator.INVERT;
            if (p.isBool()) return op == UnaryOperator.INVERT;
        }
        return false;
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(UnaryExpression e) {
        var o = optimize(e.operand());
        if (o.a() instanceof LiteralExpression le) {
            return optimizeLit(e, le);
        }
        if (checkUnaryOperate(e.operator(), o.b())) {
            var n = new UnaryExpression(e.pos(), e.operator(), o.a());
            return Groups.g2(n, o.b());
        }
        return semantic("%s not support operate %s: %s",
                o.b(), e.operator(), e.pos());
    }

    private ObjectDefinition getObject(TypeDeclarer td) {
        if (td instanceof DerivedTypeDeclarer dtd) {
            if (dtd.def() instanceof ObjectDefinition cd)
                return cd;
        }
        return semantic("require class or interface: %s", td.pos());
    }

    private void assertFinalClass(ObjectDefinition def, Entity e) {
        if (!(def instanceof ClassDefinition cd) || !cd.isFinal())
            return;

        semantic("contravariance can't use for final class %s: %s", def, e.pos());
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(AssertExpression e) {
        var g = optimize(e.subject());
        if (!(g.b() instanceof DerivedTypeDeclarer srcType)) {
            return semantic("assert can't used for %s: %s", g.b(), e.pos());
        }
        var sr = srcType.refer();
        if (sr.none()) return semantic(
                "assert only for refer: %s", e.subject().pos());

        var et = analyse(e.type());
        if (!(et instanceof DerivedTypeDeclarer dstType))
            return semantic("assert can't used for %s: %s", et, e.pos());

        var dr = dstType.maybeRefer();
        if (dr.none()) return semantic(
                "type must refer: %s", dstType.pos());
        if (dr.get().required()) return semantic(
                "assert result will be nil if failed: %s", dstType.pos());

        if (!referable(dr.get(), sr, Optional.of(g.a())))
            return unreachable();

        var n = new AssertExpression(e.pos(),
                (PrimaryExpression) g.a(), dstType);
        var srcDef = getObject(srcType);
        var tgtDef = getObject(dstType);
        assertFinalClass(srcDef, e);
        assertFinalClass(tgtDef, e);

        if (assignable(tgtDef, srcDef)) {
            return Groups.g2(n, dstType);
        }
        if (tgtDef instanceof InterfaceDefinition ||
                srcDef instanceof InterfaceDefinition) {
            n.needCheck(true);
            return Groups.g2(n, dstType);
        }
        if (tgtDef instanceof ClassDefinition td &&
                srcDef instanceof ClassDefinition sd) {
            if (assignable(srcDef, tgtDef)) {
                n.needCheck(true);
                return Groups.g2(n, dstType);
            }
        }

        return semantic("inconvertible types %s to %s: %s",
                srcType, dstType, e.pos());
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(SizeofExpression se) {
        enablePhantom = true;
        analyse(se.type());

        var size = estimateSize(se.type());
        se.size(size);
        var n = new LiteralExpression(se.pos(), new IntegerLiteral(se.pos(), size));
        return Groups.g2(n, Primitive.INT.declarer(se.pos()));
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(CallExpression e) {
        var call = e.callee();
        var args = e.arguments();

        call.expectCallable(true);
        var g = optimize(call);
        if (!(g.b() instanceof FuncTypeDeclarer td))
            return semantic("%s not callable: %s", call, e.pos());

        var p = td.prototype();
        var left = p.parameterSet().types();
        if (left.size() > args.size()) {
            return semantic("missing arguments: %s", e.pos());
        } else if (left.size() < args.size()) {
            return semantic("redundant arguments: %s", e.pos());
        }

        var nArgs = new ArrayList<Expression>(args.size());
        for (int i = 0; i < left.size(); i++) {
            var l = left.get(i);
            var a = args.get(i);
            var ag = optimize(a);
            if (!assignable(l, ag.b(), Optional.of(ag.a()), a))
                return semantic("can't use %s(type '%s') as type '%s': %s",
                        a, ag.b(), l, a.pos());
            nArgs.add(ag.a());
        }

        call = (PrimaryExpression) g.a();
        if (!(call instanceof BlockExpression be
                && be.origin().has())) {
            var n = new CallExpression(e.pos(), call,
                    nArgs, Optional.of(p));
            var rt = p.returnType();
            return Groups.g2(n, rt);
        }

        var ds = (DeclarationStatement) be.block().getFirst();
        var tmpVar = ds.variables().getLast();
        var t = (FuncTypeDeclarer) tmpVar.type().must();
        var v = (PrimaryExpression) tmpVar.value().must();
        var n = new CallExpression(e.pos(), v, nArgs,
                Optional.of(t.prototype()));
        var rt = t.prototype().returnType();
        n.resultType.set(rt);
        tmpVar.value().set(n);
        tmpVar.type().set(rt);
        be.result().resultType.set(rt);
        return Groups.g2(be, rt);
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(ConvertExpression e) {
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

    private Groups.G2<Expression, TypeDeclarer> optimize(CurrentExpression e) {
        // 必定是在类的方法里
        var dt = new DerivedType(e.pos(), e.type(), TypeArguments.EMPTY);
        dt.def.set(enterClass);
        var ref = new Refer(e.pos(), PHANTOM, true, false);
        var td = new DerivedTypeDeclarer(e.pos(), dt, Optional.of(ref));
        return Groups.g2(e, td);
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(IndexOfExpression e) {
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
            // for enum
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
                ie = new LiteralExpression(e.index().pos(), idx);
                ie.resultType.set(new LiteralTypeDeclarer(ie.pos(), idx));
            }
            var t = atd.element();
            var p = e.pos();
            var i = ie;
            var n = wrapRelayExpr(a, t, _a -> {
                return new IndexOfExpression(p, (PrimaryExpression) _a, i);
            });
            return Groups.g2(n, t);
        }

        return semantic("%s not implement index: %s",
                sg.b(), e.subject().pos());
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(LambdaExpression e) {
        return unsupported("lambda");
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(LiteralExpression e) {
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
    optimizeArray(PrimaryExpression s, ArrayTypeDeclarer atd,
                  Identifier name) {
        var af = atd.getField(name);
        if (af.has()) {
            var td = Primitive.INT.declarer(s.pos());
            if (atd.length().has()) {
                var lit = new IntegerLiteral(s.pos(), atd.len());
                var n = new LiteralExpression(s.pos(), lit);
                return Groups.g2(n, td);
            }
            var n = wrapRelayExpr(s, td, _s -> {
                return new ArrayLenExpression(_s.pos(),
                        (PrimaryExpression) _s, atd);
            });
            return Groups.g2(n, td);
        }
        return semantic("array has no field %s: %s",
                name, name.pos());
    }

    private Groups.G2<Expression, TypeDeclarer>
    optimizeMember(PrimaryExpression s, ClassDefinition cd,
                   Identifier name, DerivedTypeDeclarer st,
                   TypeArguments generic) {
        if (!s.expectCallable()) {
            invalid(generic);
            var o = cd.allFields().tryGet(name);
            if (o.has()) {
                var f = o.get();
                var gm = st.gm();
                var t = gm.mapIf(f.type());
                var n = wrapRelayExpr(s, t, _s -> {
                    return new MemberOfExpression(_s.pos(),
                            (PrimaryExpression) _s,
                            name, TypeArguments.EMPTY, f);
                });
                return Groups.g2(n, t);
            }
            return semantic("class '%s' not defined member '%s': %s",
                    cd, name, s.pos());
        }

        var om = cd.allMethods().tryGet(name);
        if (om.has()) {
            var m = om.get();
            var gm = genericMap(name, st.gm(), m.generic(), generic);
            var prot = gm.instantiate(m.prototype());
            var t = new AnonFuncTypeDeclarer(s.pos(), true, prot);
            var n = wrapRelayExpr(s, t, _s -> {
                return new MethodExpression(s.pos(),
                        (PrimaryExpression) _s, m, generic);
            });
            return Groups.g2(n, t);
        }
        var of = cd.allFields().tryGet(name);
        if (of.has()) {
            invalid(generic);
            var f = of.get();
            if (f.type() instanceof FuncTypeDeclarer) {
                var t = st.gm().mapIf(f.type());
                var n = wrapRelayExpr(s, t, _s -> {
                    return new MemberOfExpression(_s.pos(),
                            (PrimaryExpression) _s, name, TypeArguments.EMPTY);
                });
                return Groups.g2(n, t);
            }
        }
        return semantic("class '%s' not defined callable '%s': %s",
                cd, name, s.pos());
    }

    private Groups.G2<Expression, TypeDeclarer>
    optimizeMember(PrimaryExpression s, DerivedTypeDeclarer st,
                   Identifier name, TypeArguments generic) {
        var def = st.def();

        if (def instanceof StructureDefinition sd) {
            invalid(generic);
            var o = sd.fields().tryGet(name);
            if (o.has()) {
                var f = o.get();
                var n = wrapRelayExpr(s, f.type(), _s -> {
                    return new MemberOfExpression(_s.pos(),
                            (PrimaryExpression) _s, name, TypeArguments.EMPTY, f);
                });
                return Groups.g2(n, f.type());
            }
        } else if (def instanceof ClassDefinition cd) {
            return optimizeMember(s, cd, name, st, generic);
        } else if (def instanceof InterfaceDefinition id) {
            if (!s.expectCallable())
                return semantic("interface has no field '%s': %s",
                        name, s.pos());
            var m = id.allMethods.tryGet(name);
            if (m.has()) {
                var gm = genericMap(name, st.gm(), m.get().generic(), generic);
                var prot = gm.instantiate(m.get().prototype());
                var t = new AnonFuncTypeDeclarer(s.pos(), true, prot);
                var n = wrapRelayExpr(s, t, _s -> {
                    return new MethodExpression(s.pos(),
                            (PrimaryExpression) _s, m.get(), generic);
                });
                return Groups.g2(n, t);
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
    optimizeEnumValue(PrimaryExpression eve,
                      EnumTypeDeclarer etd, Identifier name) {
        var o = etd.def().getField(name);
        if (o.none()) return semantic("%s has no field %s: %s",
                etd.def(), name, name.pos());

        var f = o.get();
        var n = new MemberOfExpression(eve.pos(), eve, name, TypeArguments.EMPTY, f);
        return Groups.g2(n, f.type());
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(MemberOfExpression e) {
        analyse(e.generic());
        var sg = optimize(e.subject());

        if (sg.b() instanceof DefinitionDeclarer dtd) {
            if (dtd.def() instanceof EnumDefinition ed) {
                invalid(e.generic());
                return optimizeEnum(e, ed);
            }
            return semantic("%s not support use member: %s", dtd, e.pos());
        }

        var s = (PrimaryExpression) sg.a();
        if (sg.b() instanceof ArrayTypeDeclarer atd) {
            invalid(e.generic());
            return optimizeArray(s, atd, e.member());
        }

        if (sg.b() instanceof DerivedTypeDeclarer dtd) {
            s.expectCallable(e.expectCallable());
            return optimizeMember(s, dtd, e.member(), e.generic());
        }

        if (sg.b() instanceof EnumTypeDeclarer etd) {
            invalid(e.generic());
            return optimizeEnumValue((PrimaryExpression) sg.a(), etd, e.member());
        }

        return semantic("member '%s' not defined: %s",
                e.member(), e.pos());
    }

    private TypeDeclarer optimize(NewArrayType e) {
        analyse(e.element());

        var ref = new Refer(e.pos(), STRONG, true, false);
        var lg = optimize(e.length());
        if (lg.b() instanceof PrimitiveTypeDeclarer ptd &&
                ptd.primitive().isInteger() ||
                lg.b() instanceof LiteralTypeDeclarer ltd &&
                        ltd.isInteger()) {
            e.length(lg.a());
            return new ArrayTypeDeclarer(e.pos(), e.element(),
                    Optional.empty(), Optional.of(ref));
        }

        return semantic("array length must be integer: %s",
                e.length().pos());
    }

    private TypeDeclarer optimize(NewDefinedType e) {
        var ref = new Refer(e.pos(), STRONG, true, false);
        if (e.type() instanceof PrimitiveType pt) {
            return new PrimitiveTypeDeclarer(e.pos(),
                    pt.primitive(), Optional.of(ref));
        }
        if (e.type() instanceof DerivedType dt) {
            var def = findDef(dt);
            if (def instanceof StructureDefinition ||
                    def instanceof ClassDefinition ||
                    def instanceof EnumDefinition) {
                return new DerivedTypeDeclarer(e.pos(),
                        dt, Optional.of(ref));
            }
        }
        return semantic("can't new type %s: %s",
                e.type(), e.type().pos());
    }

    private TypeDeclarer optimize(NewType e) {
        return switch (e) {
            case NewArrayType ee -> optimize(ee);
            case NewDefinedType ee -> optimize(ee);
            case null, default -> unreachable();
        };
    }

    private boolean newInitializable(
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
                // 定义类型检查
                if (argType instanceof DerivedTypeDeclarer atd) {
                    return dt.equals(atd.derivedType());
                } else if (argType instanceof ObjectTypeDeclarer atd) {
                    // 对象初始化表达式
                    var dtd = (DerivedTypeDeclarer) dstType.derefer().must();
                    return initializable(dtd, atd, (ObjectExpression) arg);
                }
            }
        } else if (e.type() instanceof NewArrayType nat) {
            if (argType instanceof ArrayTypeDeclarer atd) {
                var net = nat.element();
                var aet = atd.element();
                if (!(arg instanceof ArrayExpression ae))
                    return assignable(net, aet, Optional.empty(), arg);

                var i = 0;
                for (var ev : ae.elements()) {
                    if (assignable(net, aet, Optional.of(ev), ev)) {
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

    private Groups.G2<Expression, TypeDeclarer> optimize(NewExpression e) {
        var dstType = optimize(e.type());
        var ea = e.arg().map(this::optimize);
        if (ea.none()) {
            var n = new NewExpression(e.pos(), e.type(), Optional.empty());
            n.resultType.set(dstType);
            return Groups.g2(n, dstType);
        }
        var ag = ea.get();
        var arg = ag.a();
        var argType = ag.b();
        var n = new NewExpression(e.pos(), e.type(), Optional.of(arg));
        n.resultType.set(dstType);

        if (newInitializable(e, dstType, argType, arg)) {
            return Groups.g2(n, dstType);
        }

        return semantic("new-type %s and init-type %s not match: %s",
                e.type(), argType, arg.pos());
    }


    private Groups.G2<Expression, TypeDeclarer>
    findCallable(SymbolExpression re) {
        var s = re.symbol();

        var ov = context.findVar(s);
        if (ov.has()) {
            var v = ov.get();
            if (v.type().must() instanceof FuncTypeDeclarer ftd) {
                invalid(re.generic());
                var n = new VariableExpression(re.pos(), ov.get());
                return Groups.g2(n, ftd);
            }
        }

        var od = context.findFunc(s);
        if (od.has()) {
            var gm = genericMap(re, od.get().generic(), re.generic());
            var prot = gm.instantiate(od.get().prototype());
            var td = new AnonFuncTypeDeclarer(re.pos(), true, prot);
            return Groups.g2(re, td);
        }

        if (s.module().none() && enterClass != null) {
            var om = enterClass.allMethods().tryGet(s.name());
            if (om.has()) {
                // 叠加上继承的泛型替换
                var gm = enterClass.inherit().must().gm();
                gm = genericMap(re, gm, om.get().generic(), re.generic());
                var prot = gm.instantiate(om.get().prototype());
                // 函数调用加上`this.`前缀，方便后续处理
                var n = wrapThis(re, om.get());
                var td = new AnonFuncTypeDeclarer(re.pos(), true, prot);
                return Groups.g2(n, td);
            }
            var of = enterClass.allFields().tryGet(s.name());
            if (of.has()) {
                var f = of.get();
                if (f.type() instanceof FuncTypeDeclarer ftd) {
                    invalid(re.generic());
                    var n = wrapThis(s, f);
                    return Groups.g2(n, ftd);
                }
            }
        }

        if (enterMethod == null)
            return semantic("func '%s' not defined: %s", s, re.pos());
        return semantic("method '%s.%s' not defined: %s",
                enterClass, s, re.pos());
    }

    private Groups.G2<Expression, TypeDeclarer>
    findSymbol(SymbolExpression re) {
        var s = re.symbol();
        var o = context.findVar(s);
        if (o.has()) {
            invalid(re.generic());
            var v = o.get();
            if (v.declare() == Declare.CONST &&
                    v.value().match(e -> e instanceof LiteralExpression))
                return Groups.g2(v.value().must(), v.type().must());
            var n = new VariableExpression(re.pos(), v);
            return Groups.g2(n, v.type().must());
        }

        var f = context.findFunc(s);
        if (f.has()) {
            var gm = genericMap(re, f.get().generic(), re.generic());
            var prot = gm.instantiate(f.get().prototype());
            var td = new AnonFuncTypeDeclarer(re.pos(), true, prot);
            return Groups.g2(re, td);
        }

        var t = context.findType(s);
        if (t.has()) {
            invalid(re.generic());
            var td = new DefinitionDeclarer(s.pos(), t.get());
            return Groups.g2(re, td);
        }

        if (enterClass != null && s.module().none()) {
            var of = enterClass.allFields().tryGet(s.name());
            if (of.has()) {
                invalid(re.generic());
                // 表达式加上`this.`前缀，方便后续处理
                return Groups.g2(wrapThis(re.symbol(), of.get()), of.get().type());
            }
        }

        return semantic("undefined symbol '%s': %s", s, s.pos());
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(SymbolExpression e) {
        analyse(e.generic());

        if (e.expectCallable())
            return findCallable(e);

        return findSymbol(e);
    }

    private TypeDeclarer analyseArrayNest(
            List<Groups.G2<Expression, TypeDeclarer>> gs) {
        var o = gs.stream().max(Comparator.comparingLong(g -> {
            var t = (ArrayTypeDeclarer) g.b();
            return t.len();
        }));
        if (o.isEmpty()) return unreachable();
        return o.get().b();
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(ArrayExpression e) {
        var es = e.elements();
        e.type().use(this::analyse);

        if (es.isEmpty()) {
            if (e.type().has()) return Groups.g2(e, e.type().get());

            var l = new LiteralExpression(e.pos(), new IntegerLiteral(e.pos(), 0));
            var t = new ArrayTypeDeclarer(e.pos(), new VoidTypeDeclarer(e.pos()),
                    Optional.of(l), Optional.empty(), true);
            t.len(0);
            return Groups.g2(e, t);
        }
        e.type().use(t -> {
            if (t.len() >= e.size()) return;
            semantic("array length '%s' less than number of values: %s",
                    t.length(), t.pos());
        });

        var gs = es.stream().map(this::optimize).toList();
        var et = gs.getFirst().b();
        if (gs.getFirst().b() instanceof ArrayTypeDeclarer _at
                && _at.refer().none()) { // 如果是嵌套数组，找最长的
            et = analyseArrayNest(gs);
        }
        var values = new ArrayList<Expression>(e.size());
        var i = 0;
        for (var g : gs) {
            values.add(g.a());
            var v = es.get(i);

            var a = Optional.of(g.a());
            if (e.type().has()) {
                var t = e.type().get();
                if (!assignable(t.element(), g.b(), a, g.a()))
                    return semantic("can't use '%s' as type '%s': %s",
                            v, t.element(), v.pos());
                g.a().resultType.set(t);
            } else {
                if (!assignable(et, g.b(), a, g.a())) {
                    return semantic("type incompatible of value '%s' and value '%s': %s",
                            et, v, v.pos());
                }
                g.a().resultType.set(et);
            }
        }
        var n = new ArrayExpression(e.pos(), values, e.type());
        if (e.type().has()) return Groups.g2(n, e.type().get());

        var length = new IntegerLiteral(e.pos(), es.size());
        var le = new LiteralExpression(e.pos(), length);

        var atd = new ArrayTypeDeclarer(e.pos(),
                e.type().map(ArrayTypeDeclarer::element).getOrElse(et),
                Optional.of(le), Optional.empty(), true);
        atd.len(es.size());
        return Groups.g2(n, atd);
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(ObjectExpression oe) {
        oe.type().use(this::analyse);

        var entries = new IdentifierTable<Expression>(oe.entries().size());
        var types = new IdentifierTable<TypeDeclarer>(oe.entries().size());
        for (var n : oe.entries().nodes()) {
            var g = optimize(n.value());
            entries.add(n.key(), g.a());
            types.add(n.key(), g.b());
        }
        var ot = new ObjectTypeDeclarer(oe.pos(), types, oe.type());
        var n = new ObjectExpression(oe.pos(), entries, oe.type());
        if (!oe.type().has()) return Groups.g2(n, ot);

        var dtd = oe.type().get();
        if (initializable(dtd, ot, n)) return Groups.g2(n, dtd);

        return semantic("can't init: %s", n.pos());
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(ParenExpression e) {
        return optimize(e.child());
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(BlockExpression e) {
        context.enterScope();
        for (var s : e.block()) analyse(s);
        var r = optimize(e.result());
        e.result(r.a());
        context.exitScope(e);
        return Groups.g2(e, r.b());
    }

    private void dereferable(TypeDeclarer td) {
        if (td instanceof ArrayTypeDeclarer)
            semantic("can't de-refer array-refer: %s", td.pos());
        if (td.maybeRefer().none())
            semantic("can't de-refer a value: %s", td.pos());
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(DereferExpression e) {
        var g = optimize(e.subject());
        dereferable(g.b());

        var s = (PrimaryExpression) g.a();
        var t = g.b().derefer().must();
        var n = wrapRelayExpr(s, t, _s -> {
            return new DereferExpression(e.pos(), (PrimaryExpression) _s);
        });
        return Groups.g2(n, t);
    }

    private Groups.G2<Expression, TypeDeclarer> optimize(PairsExpression e) {
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
    // relay: 检查到临时对象，内部生成一个relay变量指向它，方便后面的自动内存管理
    // 例如：“new(A).run(); ” 没有变量指向new创建的临时对象，会导致引用计数无法释放
    // 这种会重构成block表达式
    //

    private boolean needRelay(TypeDeclarer t) {
        if (!low) return false;
        if (t.maybeRefer().has()) return true; // 引用需要释放

        // 返回值的情况
        if (t instanceof DerivedTypeDeclarer dtd) {
            if (!(dtd.def() instanceof ClassDefinition cd))
                return false;
            // 如果是类对象则需要释放
            for (var f : cd.allFields()) {
                if (needRelay(f.type()))
                    return true;
            }
            return false;
        }
        if (t instanceof ArrayTypeDeclarer atd) {
            if (atd.len() == 0) return false;
            // 检查数组元素
            return needRelay(atd.element());
        }
        return false;
    }

    private boolean needRelay(Expression e) {
        if (!low) return false;
        if (e instanceof NewExpression)
            return true; // new创建的一定是
        if (e instanceof CallExpression ce) {
            var p = ce.prototype().must();
            if (p.returnSet().none()) return false; // 无返回值
            var t = p.returnSet().get();
            return needRelay(t);
        }
        if (e instanceof BlockExpression be
                && be.origin().has()) {
            return needRelay(be.resultType.must());
        }
        return false;
    }

    private Variable makeTmpVar(String name, Expression e) {
        var im = immutable(e, false);
        var vn = new Identifier(e.pos(), name, true);
        return new Variable(e.pos(), Modifier.empty(),
                im ? Declare.CONST : Declare.VAR,
                vn, e.resultType, Lazy.of(e));
    }

    private VariableExpression makeRelay(Expression e) {
        var v = makeTmpVar("feng$tmp_relay", e);
        var ve = new VariableExpression(e.pos(), v);
        ve.resultType.set(v.type());
        return ve;
    }

    private Expression wrapRelay(
            Expression s, TypeDeclarer t,
            Function<? super Expression, ? extends Expression> c) {
        var relay = makeRelay(s);
        var n = c.apply(relay);
        n.resultType.set(t);
        var v2 = makeTmpVar("feng$tmp_result", n);

        var ds = new DeclarationStatement(n.pos(),
                CommonUtil.list(relay.variable(), v2));
        var result = new VariableExpression(v2.pos(), v2);
        var be = new BlockExpression(n.pos(),
                CommonUtil.list(ds), result);
        be.stack(ds.variables());
        be.origin().set(n);
        be.resultType.set(t);
        return be;
    }

    private Expression wrapRelayExpr(
            Expression s, TypeDeclarer t,
            Function<? super Expression, ? extends Expression> c) {
        if (!needRelay(s)) return c.apply(s);
        return wrapRelay(s, t, c);
    }

    private Expression wrapRelayExpr(
            Expression a, Expression b,
            TypeDeclarer t,
            BiFunction<? super Expression, ? super Expression,
                    ? extends Expression> c) {
        var ca = needRelay(a);
        var cb = needRelay(b);
        if (!ca && !cb) return c.apply(a, b);

        if (!cb)
            return wrapRelayExpr(a, t, _a -> c.apply(_a, b));
        if (!ca)
            return wrapRelayExpr(b, t, _b -> c.apply(a, _b));

        var v1 = makeTmpVar("feng$tmp_relay", a);
        var r1 = new VariableExpression(v1.pos(), v1);
        var v2 = makeTmpVar("feng$tmp_relay", a);
        var r2 = new VariableExpression(v1.pos(), v2);
        var n = c.apply(r1, r2);
        n.resultType.set(t);
        var vr = makeTmpVar("feng$tmp_result", n);

        var ds = new DeclarationStatement(n.pos(), List.of(v1, v2, vr));
        var result = new VariableExpression(vr.pos(), vr);
        var be = new BlockExpression(n.pos(), List.of(ds), result);
        be.stack(ds.variables());
        be.origin().set(n);
        return be;
    }

    private Operand wrapRelayOperand(
            PrimaryExpression s,
            Function<PrimaryExpression, Operand> c) {
        if (!needRelay(s)) return c.apply(s);

        var ve = makeRelay(s);
        var n = c.apply(ve);
        n.relay().add(ve.variable());
        return n;
    }


    // macro

    private void macro(ClassDefinition cd) {
        // TODO: macro
    }


    // attribute

    private Entity analyse(AttributeDefinition ad) {
        return unsupported("attribute");
    }
}
