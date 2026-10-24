package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.GenericType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameter;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.oop.InterfaceMethod;
import org.cossbow.feng.ast.proc.FixedParameter;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.var.FieldOperand;
import org.cossbow.feng.ast.var.VariableOperand;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.*;

import static org.cossbow.feng.ast.Position.ZERO;
import static org.cossbow.feng.ast.dcl.ReferKind.STRONG;

/**
 * AST normalization pass that converts all {@link AnonFuncTypeDeclarer}
 * (anonymous function types like {@code func(int)bool}) into
 * {@link NamedFuncTypeDeclarer} backed by auto-generated
 * {@link PrototypeDefinition} entries.
 * <p>
 * Deduplication is by structural signature: return type + parameter
 * types. Two {@code func(int)bool} in different places map to the
 * same {@code PrototypeDefinition}.
 */
public class AnonFuncNormalizer {

    private final AnalyseSymbolTable ast;
    private final Map<String, PrototypeDefinition> protoMap = new LinkedHashMap<>();

    /**
     * 收集到的匿名类（类 lambda lowering 产物）。
     */
    private final List<ClassDefinition> anonClasses = new ArrayList<>();

    /**
     * 嵌套类 lambda 的捕获字段上下文：外层类 lambda 合成后，
     * 其 lambda 体内的捕获变量引用要重写为 {@code $self->字段}。
     */
    private final Deque<CaptureCtx> capFieldsStack = new ArrayDeque<>();

    /**
     * 类 lambda 捕获上下文：匿名类 + 捕获变量→字段映射。
     * 用于把捕获引用重写为 {@code $self->字段}。
     */
    private record CaptureCtx(ClassDefinition anonClass,
                              Map<Variable, ClassField> fields) {
    }

    public AnonFuncNormalizer(AnalyseSymbolTable ast) {
        this.ast = ast;
    }

    public void normalize() {
        collectFromClasses();
        collectFromStructures();
        collectFromFunctions();
        collectFromGlobals();
        // 类 lambda → 匿名类 lowering 必须在 protoMap 早退之前执行：
        // 即使不存在匿名函数类型，类 lambda 仍可能存在。
        if (!anonClasses.isEmpty())
            rebuildClassGraph();
        if (protoMap.isEmpty()) return;

        var allProtos = new ArrayList<>(
                ast.dagPrototypes.all());
        allProtos.addAll(protoMap.values());

        // Build dependency edges: if prototype A's return/param types
        // reference prototype B, add edge B→A so B is declared first.
        // Skip generic prototypes — they have type vars and won't be
        // emitted as typedefs (filtered by declareProtoTypedefs later).
        var keyToProto = new HashMap<String, PrototypeDefinition>();
        for (var pd : allProtos) {
            if (pd.prototype().hasTypeVar()) continue;
            keyToProto.put(protoKey(pd.prototype()), pd);
        }
        var edges = new ArrayList<Groups.G2<PrototypeDefinition, PrototypeDefinition>>();
        for (var pd : allProtos) {
            if (pd.prototype().hasTypeVar()) continue;
            for (var depKey : protoDeps(pd.prototype())) {
                var dep = keyToProto.get(depKey);
                if (dep != null && dep != pd) {
                    edges.add(Groups.g2(dep, pd));
                }
            }
        }

        ast.dagPrototypes = DAGGraph.make(allProtos, edges);
    }

    /**
     * Collect proto keys of {@link FuncTypeDeclarer} types nested inside
     * a prototype's return type and parameter types.
     */
    private Set<String> protoDeps(Prototype pt) {
        var deps = new HashSet<String>();
        pt.returnSet().use(td -> collectProtoDeps(td, deps));
        for (var p : pt.parameterSet()) {
            if (p instanceof FixedParameter fp) {
                collectProtoDeps(fp.type(), deps);
            }
        }
        return deps;
    }

    private void collectProtoDeps(TypeDeclarer td, Set<String> keys) {
        if (td instanceof FuncTypeDeclarer ftd) {
            keys.add(protoKey(ftd.prototype()));
            return;
        }
        if (td instanceof ArrayTypeDeclarer atd) {
            collectProtoDeps(atd.element(), keys);
        } else if (td instanceof TupleTypeDeclarer ttd) {
            for (var t : ttd.elements()) {
                collectProtoDeps(t, keys);
            }
        }
    }

    // ---- classes ----

    private void collectFromClasses() {
        for (var cd : ast.dagClasses) {
            for (var cf : cd.fields().values()) {
                cf.type(replaceAnon(cf.type()));
            }
            for (var cm : cd.methods()) {
                normalizePrototype(cm.prototype());
                cm.procedure().use(this::walkProcedure);
            }
        }
    }

    private void normalizePrototype(Prototype pt) {
        pt.returnSet().use(ret -> {
            var newRet = replaceAnon(ret);
            if (newRet != ret) pt.returnSet(Optional.of(newRet));
        });
        for (var p : pt.parameterSet()) {
            if (p instanceof FixedParameter fp) {
                var newType = replaceAnon(fp.type());
                if (newType != fp.type()) fp.type(newType);
            }
        }
    }

    // ---- structures ----

    private void collectFromStructures() {
        for (var sd : ast.dagStructures) {
            for (var sf : sd.fields()) {
                sf.type(replaceAnon(sf.type()));
            }
        }
    }

    // ---- functions ----

    private void collectFromFunctions() {
        for (var fd : ast.functionList) {
            if (fd.builtin()) continue;
            normalizePrototype(fd.prototype());
            fd.procedure().use(this::walkProcedure);
        }
        ast.main.use(fd -> {
            normalizePrototype(fd.prototype());
            fd.procedure().use(this::walkProcedure);
        });
    }

    // ---- globals ----

    private void collectFromGlobals() {
        // constVars is constants that know in analyzing
        for (var gv : ast.dagVars) {
            gv.type().set(replaceAnon(gv.type().must()));
            gv.value().use(this::walkExpr);
        }
    }

    // ---- class lambda lowering ----

    /**
     * 收集匿名类到 dagClasses 依赖图。匿名类 parent 是 builtin Object
     * （被排除在 dagClasses 之外），因此无继承入边；但值类型捕获字段
     * （内嵌类值，如 {@code var b Box} 的 {@code Box}）要求目标类结构先
     * 于匿名类结构发射（C 中内嵌非完整类型字段不合法），故补字段类型
     * 依赖边（同 {@code SemanticAnalyzer.findInitDeps} 的字段规则）。
     */
    private void rebuildClassGraph() {
        var nodes = new ArrayList<ClassDefinition>(
                ast.dagClasses.all().size() + anonClasses.size());
        nodes.addAll(ast.dagClasses.all());
        nodes.addAll(anonClasses);
        var edges = new ArrayList<Groups.G2<ClassDefinition, ClassDefinition>>(
                ast.dagClasses.edges().size());
        edges.addAll(ast.dagClasses.edges());
        var index = new HashMap<ClassDefinition, Integer>();
        for (int i = 0; i < nodes.size(); i++) index.put(nodes.get(i), i);
        for (var ac : anonClasses) {
            for (var cf : ac.fields().values()) {
                for (var dep : valueClassDeps(cf.type())) {
                    if (dep == ac || dep.builtin()) continue;
                    var a = index.get(dep);
                    var b = index.get(ac);
                    if (a == null || b == null || a >= b) continue;
                    edges.add(Groups.g2(dep, ac));
                }
            }
        }
        ast.dagClasses = DAGGraph.make(nodes, edges);
    }

    /**
     * 值类型字段依赖的类（镜像 {@code SemanticAnalyzer.getClassTypeField}）：
     * 引用字段（有 refer）只存指针，不需要目标类完整定义；
     * 内嵌类值/值数组/元组需要目标类先于本类定义。
     */
    private static List<ClassDefinition> valueClassDeps(TypeDeclarer t) {
        if (t instanceof DerivedTypeDeclarer dtd) {
            if (dtd.refer().has()) return List.of();
            if (dtd.def() instanceof ClassDefinition cd)
                return List.of(cd);
            return List.of();
        }
        if (t instanceof ArrayTypeDeclarer atd) {
            if (atd.refer().has()) return List.of();
            return valueClassDeps(atd.element());
        }
        if (t instanceof TupleTypeDeclarer ttd) {
            var deps = new ArrayList<ClassDefinition>();
            for (var e : ttd.elements()) deps.addAll(valueClassDeps(e));
            return deps;
        }
        return List.of();
    }

    /**
     * 将类 lambda 合成为匿名类并替换为 {@code new(Anon, {cap=...})}。
     * <p>
     * 合成步骤：
     * 1. 生成匿名符号 {@code Anon_<hex>}（unnamed，C 输出不带头）与 DerivedType。
     * 2. 捕获变量 → 匿名字段；目标接口唯一方法 → 匿名类唯一方法。
     * 3. 手工接线（同 Exception 静态块）：allFields/allMethods 填充、
     *    parent=Object、ancestors 收录、allImpls 收录目标接口。
     * 4. 在匿名方法体内把捕获变量引用重写为 {@code $self->字段}。
     * 5. 返回 {@code new(Anon, {cap=原始变量表达式...})}。
     */
    private Expression lowerClassLambda(ClassLambdaExpression e) {
        var pos = e.pos();
        var iface = e.targetIface();
        var im = singleMethod(iface);

        // ---- 匿名符号 ----
        // 命名符号（非 unnamed）并带定义模块：类型名 CWriter.write(Symbol) 与 meta 名
        // ClassMeta.symbolName 同为 [module$]$Anon_<hex>，两路一致。模块不可缺：泛型
        // 具体化时 use 点用 Mangle.name(dt)（[module$]Anon_<hex>_<args>），decl 点用
        // symbolName(Mangle.symbol(dt))（恒在名称前加 '$'）；模块为空时前者无 '$'
        // 前缀而后者有，两路不一致（Feng$meta_Anon_ vs Feng$meta_$Anon_）。
        var anonSymbol = new Symbol(pos,
                ast.module.get().map(FModule::path),
                new Identifier(pos, "Anon_" + CommonUtil.rand()));

        // ---- 泛型提升（P6）----
        // 外层泛型作用域的类型变量（泛型函数/泛型类的 T）→ 匿名类类型参数。
        // 克隆（保 id）作匿名类自身参数：mono 期 concretizeClass 用 {T'→实参}
        // 替换字段/方法/impl 里的引用，同 id 使外层函数 gm（{T→实参}）与
        // 匿名类 gm（{T'→实参}）都靠 HashMap equals 命中（branchGm 模式）。
        var lifted = new ArrayList<TypeParameter>(e.typeVars().size());
        for (var tp : e.typeVars())
            lifted.add((TypeParameter) tp.clone());
        var liftedParams = new IdentifierMap<TypeParameter>(lifted.size());
        for (var tp : lifted) liftedParams.add(tp.name(), tp);
        var generic = new TypeParameters(pos, liftedParams);

        // ---- 目标接口 impl ----
        // 用调用点实例化后的 DerivedType（e.targetType()）：泛型接口在调用点
        // 已是 ValueGetter`T`/ValueGetter`int`（P3 经 dt.gm() 实例化原型），
        // concretizeClass 再经 {T'→实参} 映射到具体接口。裸 iface.link(pos)
        // 只填接口自身形参，无法表达调用点实参。
        var implMap = new SymbolMap<DerivedType>();
        var targetDt = ((DerivedTypeDeclarer) e.targetType()).derivedType();
        implMap.add(iface.symbol(), targetDt);

        // ---- 捕获变量 → 字段 ----
        // 捕获字段保留原变量的 const/var：const 捕获变量在 lambda 体内
        // 不可写（语义分析已拒），字段同步 const 保持一致。
        var fieldsMap = new IdentifierMap<ClassField>(e.captures().size());
        var capFields = new LinkedHashMap<Variable, ClassField>();
        for (var v : e.captures()) {
            var td = replaceAnon(v.type().must());
            var cf = new ClassField(pos, Modifier.empty(),
                    v.declare(), v.name(), td);
            fieldsMap.add(cf.name(), cf);
            capFields.put(v, cf);
        }

        // ---- 接口唯一方法 → 匿名类方法 ----
        // 用 P3 实例化后的推断原型（e.inferredPrototype()）：返回/参数类型
        // 已含调用点实参或外层 T（同 id → 匿名类 gm 命中）。
        var ipt = e.inferredPrototype();
        // 参数用类 lambda 自己的原型（其 FixedParameter 已在 P3 analyse(nproc)
        // 时创建了 Variable 缓存，方法体引用的是同一批变量）；返回类型与
        // catchAll 对齐接口方法。
        var ppt = e.procedure().prototype();
        var nprot = new Prototype(ppt.pos(), ppt.parameterSet(),
                ipt.returnSet(), ipt.catchAll());
        var nproc = new Procedure(e.procedure().pos(), nprot,
                e.procedure().body(), e.procedure().labels());
        var ncm = new ClassMethod(pos, Modifier.empty(), im.name(),
                TypeParameters.empty(), im.escaped(), im.unmodifiable(),
                nprot, Optional.of(nproc), im.returnThis());
        var methodsMap = new IdentifierMap<ClassMethod>(1);
        methodsMap.add(ncm.name(), ncm);

        // ---- 匿名类定义 ----
        var anonClass = new ClassDefinition(pos, Modifier.empty(),
                anonSymbol, generic, false,
                Optional.of(ClassDefinition.ObjectClass.link(pos)),
                implMap, fieldsMap, methodsMap, new MacroTable());

        // ---- 手工接线（同 Exception 静态块） ----
        anonClass.allFields().addAll(anonClass.fields());
        anonClass.allMethods().addAll(anonClass.methods());
        anonClass.fields().each(f -> f.master(anonClass));
        anonClass.methods().each(m -> m.master(anonClass));
        anonClass.parent().set(ClassDefinition.ObjectClass);
        ClassDefinition.ObjectClass.markInherited();
        anonClass.ancestors().add(ClassDefinition.ObjectClass);
        anonClass.allImpls().add(iface);

        anonClasses.add(anonClass);

        // ---- 匿名类型（def 已接线，write 依赖） ----
        // 调用点类型参数 = 外层 T（原 T，非克隆）：外层函数 gm（{T→实参}）
        // 扫描 new 时映射为 Anon_具体。anonClass.link 设置 def + gm={T'→T}。
        var typeArgs = new ArrayList<TypeDeclarer>(e.typeVars().size());
        for (var tp : e.typeVars())
            typeArgs.add(new GenericTypeDeclarer(pos,
                    new GenericType(pos, tp)));
        var anonDt = anonClass.link(pos,
                new TypeArguments(pos, typeArgs));

        // ---- 捕获重写（匿名方法体内） ----
        capFieldsStack.push(new CaptureCtx(anonClass, capFields));
        try {
            walkProcedure(nproc);
        } finally {
            capFieldsStack.pop();
        }

        // ---- 替换为 new 表达式 ----
        // 捕获变量若是外层匿名类的捕获，须以 $self->字段 形式传递
        // （此时栈中只剩外层上下文，rewriteCapture 命中即重写）。
        var entries = new IdentifierMap<Expression>(e.captures().size());
        for (var v : e.captures()) {
            var rw = rewriteCapture(new VariableExpression(pos, v));
            entries.add(v.name(), rw != null ? rw
                    : new VariableExpression(pos, v));
        }
        var objExp = new ObjectExpression(pos, entries,
                Optional.of(new DerivedTypeDeclarer(pos, anonDt)));
        var ne = new NewExpression(pos,
                new NewDefinedType(pos, anonDt),
                Optional.of(objExp));
        ne.resultType.set(new DerivedTypeDeclarer(pos, anonDt,
                new Refer(pos, STRONG, true, false)));
        return ne;
    }

    private static InterfaceMethod singleMethod(InterfaceDefinition id) {
        var ms = id.methods();
        if (ms.isEmpty()) {
            return id.allMethods().getValue(0);
        }
        return ms.getValue(0);
    }

    /**
     * 把捕获变量引用重写为 {@code $self->字段}。
     * 只查最内层上下文：内层匿名类的捕获字段一定包含该变量
     * （捕获集是 lambda 体引用到的所有外层变量）。
     */
    private Expression rewriteCapture(VariableExpression ve) {
        var ctx = capFieldsStack.peek();
        if (ctx == null) return null;
        var cf = ctx.fields().get(ve.variable());
        if (cf == null) return null;

        var pos = ve.pos();
        var anonDt = ctx.anonClass().link(pos);
        var self = new CurrentExpression(pos,
                ctx.anonClass().symbol(), true);
        self.resultType.set(new DerivedTypeDeclarer(pos, anonDt,
                new Refer(pos, STRONG, true, false)));
        var n = new MemberOfExpression(pos, self, cf.name(), cf);
        n.resultType.set(cf.type());
        return n;
    }

    /**
     * 把捕获变量作赋值目标（左值）的重写为 {@code $self->字段 = ...}。
     * 与 {@link #rewriteCapture} 对称：右值引用已重写，左值若不重写
     * 会生成对已不存在的原局部变量的赋值（悬空引用）。
     */
    private FieldOperand rewriteCaptureOperand(VariableOperand vo) {
        var ctx = capFieldsStack.peek();
        if (ctx == null) return null;
        if (vo.variable().none()) return null;
        var cf = ctx.fields().get(vo.variable().must());
        if (cf == null) return null;

        var pos = vo.pos();
        var anonDt = ctx.anonClass().link(pos);
        var self = new CurrentExpression(pos,
                ctx.anonClass().symbol(), true);
        self.resultType.set(new DerivedTypeDeclarer(pos, anonDt,
                new Refer(pos, STRONG, true, false)));
        var n = new FieldOperand(pos, self, cf.name());
        n.type().set(cf.type());
        return n;
    }

    // ---- procedure / statement walk ----

    private void walkProcedure(Procedure proc) {
        var body = walkBlockStmt(proc.body());
        if (body != proc.body()) proc.body(body);
    }

    private BlockStatement walkBlockStmt(BlockStatement bs) {
        var list = bs.list();
        boolean changed = false;
        var buf = new ArrayList<Statement>(list.size());
        for (var s : list) {
            var ns = walkStmt(s);
            buf.add(ns);
            if (ns != s) changed = true;
        }
        if (!changed) return bs;
        bs.list(buf);
        return bs;
    }

    /**
     * Walk statements, replacing any class-lambda (and nested class
     * lambdas) with their anonymous-class {@code new} expression.
     * Returns the replacement (may be the same instance).
     */
    private Statement walkStmt(Statement s) {
        switch (s) {
            case DeclarationStatement ds -> {
                for (var v : ds.variables()) {
                    v.type().set(replaceAnon(v.type().must()));
                    if (v.value().has()) {
                        var nv = walkExpr(v.value().must());
                        if (nv != v.value().must())
                            v.value().set(nv);
                    }
                }
            }
            case BlockStatement bs -> walkBlockStmt(bs);
            case CallStatement cs -> {
                var nc = walkExpr(cs.call());
                if (nc != cs.call() && nc instanceof CallExpression nce)
                    cs.call(nce);
            }
            case ReturnStatement rs -> {
                if (rs.result().has()) {
                    var nr = walkExpr(rs.result().get());
                    if (nr != rs.result().get())
                        rs.result(Optional.of(nr));
                }
            }
            case IfStatement is -> {
                if (is.init().has()) {
                    var ns = walkStmt(is.init().get());
                    if (ns != is.init().get()) is.init(Optional.of(ns));
                }
                var nc = walkExpr(is.condition());
                if (nc != is.condition()) is.condition(nc);
                walkBlockStmt(is.yes());
                if (is.not().has()) {
                    var ns = walkStmt(is.not().get());
                    if (ns != is.not().get()) is.not(Optional.of(ns));
                }
            }
            case ForStatement fs -> {
                if (fs instanceof ConditionalForStatement cfs) {
                    if (cfs.initializer().has()) {
                        var ns = walkStmt(cfs.initializer().get());
                        if (ns != cfs.initializer().get())
                            cfs.initializer(Optional.of(ns));
                    }
                    var nc = walkExpr(cfs.condition());
                    if (nc != cfs.condition()) cfs.condition(nc);
                    walkBlockStmt(cfs.body());
                } else if (fs instanceof IterableForStatement ifs) {
                    var ni = walkExpr(ifs.iterable());
                    if (ni != ifs.iterable()
                            && ni instanceof PrimaryExpression npe)
                        ifs.iterable(npe);
                    walkBlockStmt(ifs.body());
                }
            }
            case SwitchStatement ss -> {
                if (ss.init().has()) {
                    var ns = walkStmt(ss.init().get());
                    if (ns != ss.init().get()) ss.init(Optional.of(ns));
                }
                var nv = walkExpr(ss.value());
                if (nv != ss.value()) ss.value(nv);
                for (var br : ss.branches()) walkStmt(br);
                ss.defaultBranch().use(this::walkStmt);
            }
            case AssignmentsStatement as -> {
                for (var a : as.list()) {
                    // 左值 operand 也要走：捕获变量作赋值目标时
                    // （c = c + 1）须重写为 $self->字段，否则生成对
                    // 已不存在原局部变量的赋值（悬空引用）。
                    if (a.operand() instanceof VariableOperand vo
                            && !capFieldsStack.isEmpty()) {
                        var no = rewriteCaptureOperand(vo);
                        if (no != null) a.operand(no);
                    }
                    var nv = walkExpr(a.value());
                    if (nv != a.value()) a.value(nv);
                }
            }
            case SwitchBranch sb -> walkBlockStmt(sb.body());
            case TryStatement ts -> {
                walkBlockStmt(ts.body());
                for (var cc : ts.catchClauses()) {
                    cc.argument().type().set(
                            replaceAnon(cc.argument().type().must()));
                    walkBlockStmt(cc.body());
                }
                if (ts.finallyClause().has())
                    walkBlockStmt(ts.finallyClause().get());
            }
            case StaticIfStatement sis -> {
                // 分析期不再折叠 static if：两分支都含可执行语句，必须遍历
                //（分支内的匿名函数同样要归一化，否则缺具名原型）
                walkBlockStmt(sis.yes());
                if (sis.not().has()) walkStmt(sis.not().get());
            }
            default -> {
            }
        }
        return s;
    }

    // ---- expression walk ----

    /**
     * Walk expressions, replacing any class-lambda (and nested class
     * lambdas) with their anonymous-class {@code new} expression.
     * Returns the replacement (may be the same instance).
     */
    private Expression walkExpr(Expression e) {
        if (e == null) return null;
        if (e instanceof ClassLambdaExpression cl)
            return lowerClassLambda(cl);
        if (e instanceof VariableExpression ve && !capFieldsStack.isEmpty()) {
            var n = rewriteCapture(ve);
            if (n != null) return n;
        }

        switch (e) {
            case CallExpression ce -> {
                // 被调方也要走：捕获变量作被调方（如 compose 体里的
                // f(g(x))）时，callee 是 VariableExpression，必须重写为
                // $self->字段，否则匿名类方法体引用自由变量生成悬空引用。
                boolean changed = false;
                var nc = walkExpr(ce.callee());
                var callee = nc instanceof PrimaryExpression npe
                        ? npe : ce.callee();
                if (callee != ce.callee()) changed = true;
                var args = new ArrayList<Expression>(ce.arguments().size());
                for (var a : ce.arguments()) {
                    var na = walkExpr(a);
                    args.add(na);
                    if (na != a) changed = true;
                }
                if (!changed) return ce;
                var n = ce.prototype().has()
                        ? new CallExpression(ce.pos(), callee, args,
                                ce.variadic(), ce.prototype().get())
                        : new CallExpression(ce.pos(), callee, args,
                                ce.variadic());
                copyTypes(ce, n);
                return n;
            }
            case NewExpression ne -> {
                // NewArrayType has element type that may contain AnonFunc
                if (ne.type() instanceof NewArrayType nat) {
                    nat.element(replaceAnon(nat.element()));
                }
                if (!ne.arg().has()) return ne;
                var na = walkExpr(ne.arg().get());
                if (na == ne.arg().get()) return ne;
                var n = new NewExpression(ne.pos(), ne.type(),
                        Optional.of(na));
                copyTypes(ne, n);
                return n;
            }
            case ConvertExpression ce -> {
                var no = walkExpr(ce.operand());
                if (no == ce.operand()) return ce;
                var n = new ConvertExpression(ce.pos(), ce.primitive(), no);
                copyTypes(ce, n);
                return n;
            }
            case ObjectExpression oe -> {
                boolean changed = false;
                var entries = new IdentifierMap<Expression>(oe.entries().size());
                for (var node : oe.entries().nodes()) {
                    var nv = walkExpr(node.value());
                    entries.add(node.key(), nv);
                    if (nv != node.value()) changed = true;
                }
                if (!changed) return oe;
                var n = new ObjectExpression(oe.pos(), entries, oe.type());
                copyTypes(oe, n);
                return n;
            }
            case ArrayExpression ae -> {
                ae.type().use(atd -> atd.element(
                        replaceAnon(atd.element())));
                boolean changed = false;
                var elems = new ArrayList<Expression>(ae.elements().size());
                for (var item : ae.elements()) {
                    var ni = walkExpr(item);
                    elems.add(ni);
                    if (ni != item) changed = true;
                }
                if (!changed) return ae;
                ae.elements(elems);
                return ae;
            }
            case BinaryExpression be -> {
                var nl = walkExpr(be.left());
                if (nl != be.left()) be.left(nl);
                var nr = walkExpr(be.right());
                if (nr != be.right()) be.right(nr);
                return be;
            }
            case UnaryExpression ue -> {
                var no = walkExpr(ue.operand());
                if (no == ue.operand()) return ue;
                var n = new UnaryExpression(ue.pos(), ue.operator(), no);
                copyTypes(ue, n);
                return n;
            }
            case ConditionalExpression ce -> {
                var nc = walkExpr(ce.condition());
                var ny = walkExpr(ce.yes());
                var nn = walkExpr(ce.not());
                if (nc == ce.condition() && ny == ce.yes()
                        && nn == ce.not()) return ce;
                var n = new ConditionalExpression(ce.pos(), nc, ny, nn);
                copyTypes(ce, n);
                return n;
            }
            case BlockExpression be -> {
                var nb = walkBlockStmt(new BlockStatement(
                        be.pos(), be.block()));
                var nr = walkExpr(be.result());
                if (nb.list() == be.block() && nr == be.result()) return be;
                var n = new BlockExpression(be.pos(), nb.list(), nr);
                copyTypes(be, n);
                n.stack(be.stack());
                return n;
            }
            case IsExpression ie -> {
                var ns = walkExpr(ie.subject());
                if (ns == ie.subject()) return ie;
                if (!(ns instanceof PrimaryExpression npe))
                    return ie;
                var n = new IsExpression(ie.pos(), npe, ie.type());
                copyTypes(ie, n);
                return n;
            }
            case MemberOfExpression moe -> {
                var ns = walkExpr(moe.subject());
                if (ns == moe.subject()) return moe;
                if (!(ns instanceof PrimaryExpression npe))
                    return moe;
                var n = new MemberOfExpression(moe.pos(), npe,
                        moe.member(), moe.generic(), moe.field());
                copyTypes(moe, n);
                return n;
            }
            case IndexOfExpression ioe -> {
                var ns = walkExpr(ioe.subject());
                var ni = walkExpr(ioe.index());
                if (ns == ioe.subject() && ni == ioe.index()) return ioe;
                if (!(ns instanceof PrimaryExpression npe))
                    return ioe;
                var n = new IndexOfExpression(ioe.pos(), npe, ni);
                copyTypes(ioe, n);
                return n;
            }
            case SliceOfExpression soe -> {
                var ns = walkExpr(soe.subject());
                var nstart = soe.start() == null ? null
                        : walkExpr(soe.start());
                var nend = soe.end() == null ? null
                        : walkExpr(soe.end());
                if (ns == soe.subject()
                        && nstart == soe.start()
                        && nend == soe.end()) return soe;
                if (!(ns instanceof PrimaryExpression npe))
                    return soe;
                var n = new SliceOfExpression(soe.pos(), npe,
                        nstart, nend);
                copyTypes(soe, n);
                return n;
            }
            case DereferExpression de -> {
                var ns = walkExpr(de.subject());
                if (ns == de.subject()) return de;
                if (!(ns instanceof PrimaryExpression npe))
                    return de;
                var n = new DereferExpression(de.pos(), npe);
                copyTypes(de, n);
                return n;
            }
            case TupleExpression te -> {
                boolean changed = false;
                var elems = new ArrayList<Expression>(te.elements().size());
                for (var item : te.elements()) {
                    var ni = walkExpr(item);
                    elems.add(ni);
                    if (ni != item) changed = true;
                }
                if (!changed) return te;
                te.elements(elems);
                return te;
            }
            case ParenExpression pe -> {
                var nc = walkExpr(pe.child());
                if (nc == pe.child()) return pe;
                var n = new ParenExpression(pe.pos(), nc);
                copyTypes(pe, n);
                return n;
            }
            default -> {
                return e;
            }
        }
    }

    /**
     * Copy {@code resultType}/{@code expectType} from the original
     * to a rebuilt node.
     */
    private static void copyTypes(Expression from, Expression to) {
        from.resultType.use(t -> to.resultType.set(t));
        from.expectType.use(t -> to.expectType.set(t));
    }

    // ---- recursive type replacement ----

    private TypeDeclarer replaceAnon(TypeDeclarer td) {
        if (td == null) return null;
        return switch (td) {
            case AnonFuncTypeDeclarer a -> makeNamed(a);
            case DerivedTypeDeclarer dtd -> {
                var dt = dtd.derivedType();
                var newArgs = new ArrayList<TypeDeclarer>();
                boolean changed = false;
                for (var ga : dt.generic()) {
                    var na = replaceAnon(ga);
                    newArgs.add(na);
                    if (na != ga) changed = true;
                }
                if (!changed) yield dtd;
                var newDt = dt.clone();
                newDt.generic(new TypeArguments(dt.pos(), newArgs));
                yield new DerivedTypeDeclarer(dtd.pos(), newDt, dtd.refer());
            }
            case ArrayTypeDeclarer atd -> {
                var el = replaceAnon(atd.element());
                if (el == atd.element()) yield atd;
                var na = new ArrayTypeDeclarer(atd.pos(), el,
                        atd.length(), atd.refer(), atd.literal());
                if (atd.len() != null) na.len(atd.len());
                if (atd.unit() != null) na.unit(atd.unit());
                yield na;
            }
            case TupleTypeDeclarer ttd -> {
                var newElems = ttd.elements().stream()
                        .map(this::replaceAnon).toList();
                yield new TupleTypeDeclarer(ttd.pos(), newElems);
            }
            default -> td;
        };
    }

    // ---- Anon → Named conversion ----

    private TypeDeclarer makeNamed(AnonFuncTypeDeclarer a) {
        var pt = a.prototype();
        if (pt.hasTypeVar()) return a;  // unresolved generic, skip

        // Recursively normalize nested function types in the prototype
        var normalized = new Prototype(pt.pos(),
                pt.parameterSet(),
                pt.returnSet().map(this::replaceAnon),
                pt.catchAll());
        for (var p : normalized.parameterSet()) {
            if (p instanceof FixedParameter fp) {
                fp.type(replaceAnon(fp.type()));
            }
        }

        var key = protoKey(normalized);
        var pd = protoMap.get(key);
        if (pd == null) {
            pd = createProtoDef(key, normalized);
            protoMap.put(key, pd);
        }
        var dt = new DerivedType(a.pos(), pd.symbol(),
                TypeArguments.EMPTY);
        var result = new NamedFuncTypeDeclarer(a.pos(),
                a.required(), dt, Lazy.of(pd));
        result.prototype(pd.prototype());
        return result;
    }

    private PrototypeDefinition createProtoDef(String key, Prototype pt) {
        var name = new Identifier(key);
        var sym = new Symbol(ZERO, name);
        return new PrototypeDefinition(ZERO, Modifier.empty(), sym,
                TypeParameters.empty(), pt);
    }

    // ---- stable keys for dedup ----

    private String protoKey(Prototype pt) {
        var sb = new StringBuilder("Proto");
        sb.append('_');
        if (pt.returnSet().has()) sb.append(typeKey(pt.returnSet().get()));
        else sb.append("Void");
        for (var t : pt.parameterSet().types()) {
            sb.append('_').append(typeKey(t));
        }
        return sb.toString();
    }

    private String typeKey(TypeDeclarer td) {
        if (td.hasTypeVar())
            return ErrorUtil.unreachable();
        return switch (td) {
            case PrimitiveTypeDeclarer ptd -> ptd.primitive().name();
            case VoidTypeDeclarer vtd -> "void";
            case DerivedTypeDeclarer dtd -> {
                var sb = new StringBuilder();
                sb.append(dtd.derivedType().symbol().name().value());
                if (!dtd.derivedType().generic().isEmpty()) {
                    for (var ga : dtd.derivedType().generic())
                        sb.append('_').append(typeKey(ga));
                }
                if (dtd.refer().has()) sb.append("Ptr");
                yield sb.toString();
            }
            case ArrayTypeDeclarer atd -> {
                var sb = new StringBuilder("Array");
                sb.append('_').append(typeKey(atd.element()));
                if (atd.len() != null) sb.append('_').append(atd.len());
                if (atd.refer().match(r -> true)) sb.append("Ref");
                yield sb.toString();
            }
            case TupleTypeDeclarer ttd -> {
                var sb = new StringBuilder("Tuple");
                for (var e : ttd.elements()) sb.append('_').append(typeKey(e));
                yield sb.toString();
            }
            case FuncTypeDeclarer ftd -> protoKey(ftd.prototype());
            default -> td.toString();
        };
    }
}
