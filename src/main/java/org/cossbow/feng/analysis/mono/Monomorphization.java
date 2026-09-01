package org.cossbow.feng.analysis.mono;

import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.mod.ModuleManager;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.FixedParameter;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.util.Optional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * New monomorphization pass: produces truly concretized type definitions
 * (class/interface/array/tuple/prototype) rather than the old "template +
 * typeMap" wrap. Relies on {@link GenericMap} for substitution, never on
 * positional monoParams/monoArgs or string keys.
 */
public class Monomorphization {

    private final AnalyseSymbolTable table;
    /**
     * Cross-module mono instance lookup via {@link ModuleManager} (lazy create
     * on first access). A concrete instance owned by another module is written
     * into that module's table (definition-site ownership). Null in single-file
     * mode.
     */
    private final ModuleManager manager;

    /**
     * Worklist of concrete type declarers to concretize into mono definitions.
     */
    private final ArrayDeque<TypeDeclarer> pending = new ArrayDeque<>();
    /**
     * Concrete type definitions owned by this module (identity = definition
     * equals/hashCode = symbol). Cross-module instances are written into their
     * owner's set via {@link #owner(Symbol)}.
     */
    private final Set<TypeDefinition> concretized = new LinkedHashSet<>();
    /**
     * Symbol → concrete definition index for dependency resolution.
     */
    private final Map<Symbol, TypeDefinition> concreteBySymbol = new LinkedHashMap<>();
    /**
     * Concrete generic function instantiations discovered during scanning.
     */
    private final Set<FuncInstantiation> funcInsts = new LinkedHashSet<>();
    /**
     * Concrete method-level generic instantiations discovered during scanning.
     */
    private final Set<MethodInstantiation> methodInsts = new LinkedHashSet<>();
    /**
     * Concrete class symbol → its class-level {@link GenericMap} (template
     * params → concrete args). Used to rescan concrete class method bodies
     * (whose {@code T} references survive the template) during method-level
     * generic discovery.
     */
    private final Map<Symbol, GenericMap> classGmBySymbol = new LinkedHashMap<>();

    public Monomorphization(AnalyseSymbolTable ast) {
        this(ast, null);
    }

    public Monomorphization(AnalyseSymbolTable ast,
                            ModuleManager manager) {
        this.table = ast;
        this.manager = manager;
    }

    /**
     * The mono instance that owns {@code symbol} (its defining module). Falls
     * back to {@code this} for the current module or single-file mode.
     */
    private Monomorphization owner(Symbol symbol) {
        if (manager == null || symbol.module().none()) return this;
        var m = manager.mono(symbol.module().get());
        return m != null ? m : this;
    }

    /**
     * 判重/查找都必须按 definition-site ownership 走 {@link #owner}，与写入端
     * （{@code concretizeTypes}/{@code drainPending} 写
     * {@code owner(def.symbol()).concreteBySymbol}）保持一致。若直接用
     * {@code this.concreteBySymbol}，跨模块泛型（如 std 的 {@code Entry`int,int`}
     * 自引用 {@code *?Entry`int,int`}）的实例写进了 std 的 map，而 backend 的 map
     * 永远 miss → {@code collect} 反复入队 → {@code concretizeTypes} 死循环 + 内存膨胀。
     */
    private boolean concretized(Symbol symbol) {
        return owner(symbol).concreteBySymbol.containsKey(symbol);
    }

    /** {@code concretized} 的取定义版本（依赖解析用）。 */
    private TypeDefinition concreteDef(Symbol symbol) {
        return owner(symbol).concreteBySymbol.get(symbol);
    }

    public void run() {
        discover();
        discoverFuncInsts();
        concretizeTypes();
        linkConcreteParents();
        concretizeFuncs();
        discoverMethodInsts();
        // discoverMethodInsts 扫描方法级泛型具体化时会发现新类型
        // （如 map`E` 参数 func(T)E → func(int)Complex 的 AnonFuncTypeDeclarer），
        // 追加进 pending——必须再排空一次，否则 Feng$Proto_<key> typedef 缺失
        // （tuple.feng 报 unknown type name 'Feng$Proto_Complex_Int'）。
        drainPending();
    }

    /**
     * 排空 pending（幂等：已 concretized 的跳过；防死循环加保险丝）。
     */
    private void drainPending() {
        int guard = 0;
        while (!pending.isEmpty()) {
            if (++guard > 100000) {
                System.err.println("[drainPending] bail out: pending still non-empty");
                break;
            }
            var td = pending.poll();
            var def = concretizeType(td);
            if (def != null) {
                var o = owner(def.symbol());
                if (o.concretized.add(def)) {
                    o.concreteBySymbol.put(def.symbol(), def);
                }
            }
        }
    }

    /**
     * 具体化后补全类继承链：把具体化类与 dagClasses 非泛型类（如
     * {@code IntBox:SealedBox`int`}）的 {@code parent()} 与
     * {@code inherit().def()} 都重链到**具体化父类**。
     * <p>concretizeClass 只填了 {@code inherit()}（DerivedType），parent() 是
     * Lazy 且未设置；inherit().def() 仍指向模板类。后端虚派发（ExprWriter 找
     * master）、MetaWriter.parentDepth/ancestorAt、VTableBuilder.collectAncestors
     * 都沿 parent()/inherit().def() 走——断裂会把模板类当父类用
     * （generic-3 的 IntBox 报 {@code Feng$meta_SealedBox} / {@code Box$get}）。
     * 泛型父类按 mangle 符号查 concreteBySymbol；非泛型父类直接用 inherit().def()。
     */
    private void linkConcreteParents() {
        // 具体化类 + dagClasses 非泛型类（后者的 parent 由语义分析指向模板类）
        var all = new LinkedHashSet<ClassDefinition>();
        for (var def : concreteBySymbol.values()) {
            if (def instanceof ClassDefinition cd) all.add(cd);
        }
        for (var cd : table.dagClasses) {
            all.add(cd);
        }
        for (var cd : all) {
            if (cd.inherit().none()) continue;
            var idt = cd.inherit().must();
            ClassDefinition p = null;
            if (idt.generic().isEmpty()) {
                if (idt.def() instanceof ClassDefinition pcd) p = pcd;
            } else {
                var pdef = owner(idt.symbol()).concreteBySymbol.get(Mangle.symbol(idt));
                if (pdef instanceof ClassDefinition pcd) p = pcd;
            }
            if (p == null) continue;
            cd.parent().set(p);
            // inherit().def() 也重链到具体化父类：MetaWriter.ancestorAt /
            // TypeWriter 沿 inherit().def() 走时拿到具体类（模板类没有具体化
            // 的 meta 符号）。
            idt.def(p);
        }
    }

    /**
     * Phase 2: build type/unit dependency graphs. Must run after every module
     * finishes phase 1 ({@link #run()}), so cross-module instances written here
     * by other modules are all collected before bucketing.
     */
    public void buildDeps() {
        buildUnitDeps();
    }

    // ---- M1: discovery ----

    /**
     * Traverse concrete function/method bodies, struct fields and global
     * variables to discover concrete type references into {@link #pending}.
     */
    private void discover() {
        for (var fd : table.functionList) {
            if (fd.builtin()) continue;
            if (fd.generic().isEmpty())
                scanFunc(fd, GenericMap.EMPTY);
        }
        table.main.use(fd -> scanFunc(fd, GenericMap.EMPTY));

        for (var cd : table.dagClasses) {
            // Non-generic class inheriting a generic parent → parent instance.
            if (cd.generic().isEmpty()
                    && cd.inherit().has()
                    && cd.inherit().must() instanceof DerivedType idt
                    && !idt.generic().isEmpty()) {
                collect(new DerivedTypeDeclarer(idt.pos(), idt), GenericMap.EMPTY);
            }
            for (var cf : cd.fields()) {
                collect(cf.type(), GenericMap.EMPTY);
            }
            for (var cm : cd.methods()) {
                scanMethod(cm, GenericMap.EMPTY);
            }
        }

        for (var sd : table.dagStructures) {
            for (var sf : sd.fields()) {
                collect(sf.type(), GenericMap.EMPTY);
            }
        }
        for (var gv : table.constVars) {
            collect(gv.type().must(), GenericMap.EMPTY);
        }
        for (var gv : table.dagVars) {
            collect(gv.type().must(), GenericMap.EMPTY);
        }
    }

    private void scanProc(Procedure proc, GenericMap gm) {
        for (var p : proc.prototype().parameterSet()) {
            if (p instanceof FixedParameter fp) collect(fp.type(), gm);
        }
        proc.prototype().returnSet().use(t -> collect(t, gm));
        scanStmt(proc.body(), gm);
    }

    private void scanFunc(FunctionDefinition fd, GenericMap gm) {
        if (fd.procedure().none()) return;
        scanProc(fd.procedure().get(), gm);
    }

    private void scanMethod(ClassMethod cm, GenericMap gm) {
        if (cm.procedure().none()) return;
        scanProc(cm.procedure().get(), gm);
    }

    private void scanStmt(Statement stmt, GenericMap gm) {
        if (stmt instanceof DeclarationStatement ds) {
            for (var v : ds.variables()) {
                collect(v.type().must(), gm);
                v.value().use(e -> scanExpr(e, gm));
            }
        } else if (stmt instanceof BlockStatement bs) {
            for (var s : bs.list()) scanStmt(s, gm);
        } else if (stmt instanceof IfStatement is) {
            is.init().use(s -> scanStmt(s, gm));
            scanExpr(is.condition(), gm);
            scanStmt(is.yes(), gm);
            is.not().use(s -> scanStmt(s, gm));
        } else if (stmt instanceof ForStatement fs) {
            if (fs instanceof ConditionalForStatement cfs) {
                cfs.initializer().use(s -> scanStmt(s, gm));
                scanStmt(cfs.body(), gm);
            }
        } else if (stmt instanceof SwitchStatement ss) {
            for (var br : ss.branches()) scanStmt(br, gm);
        } else if (stmt instanceof CallStatement cs) {
            scanExpr(cs.call(), gm);
            cs.replace().use(s -> scanStmt(s, gm));
        } else if (stmt instanceof ReturnStatement rs) {
            rs.result().use(e -> scanExpr(e, gm));
        } else if (stmt instanceof AssignmentsStatement as) {
            for (int i = 0; i < as.list().size(); i++) {
                scanExpr(as.value(i), gm);
            }
        }
    }

    private void scanExpr(Expression e, GenericMap gm) {
        if (e instanceof CallExpression ce) {
            scanExpr(ce.callee(), gm);
            for (var a : ce.arguments()) scanExpr(a, gm);
        } else if (e instanceof SymbolExpression se && !se.generic().isEmpty()) {
            registerFuncInstantiation(se.symbol(), se.generic(), gm);
        } else if (e instanceof FunctionExpression fe && !fe.generic().isEmpty()) {
            registerFuncInstantiation(fe.symbol(), fe.generic(), gm);
        } else if (e instanceof NewExpression ne) {
            ne.resultType.use(t -> collect(t, gm));
            ne.arg().use(a -> scanExpr(a, gm));
        } else if (e instanceof MethodExpression me) {
            scanExpr(me.subject(), gm);
            // method-level generic call: `subject.method`Arg1,Arg2`(...)`
            if (!me.generic().isEmpty()) {
                var resolved = gm.mapAll(me.generic());
                if (!resolved.hasTypeVar()) {
                    me.subject().resultType.use(td -> {
                        var st = gm.mapIf(td);
                        if (st instanceof DerivedTypeDeclarer dtd
                                && !dtd.derivedType().hasTypeVar()
                                && dtd.def() instanceof ClassDefinition cd) {
                            registerMethodInst(cd, dtd.derivedType(), me, resolved);
                        }
                    });
                }
            }
        } else if (e instanceof ObjectExpression oe) {
            for (var val : oe.entries().values()) scanExpr(val, gm);
        } else if (e instanceof MemberOfExpression moe) {
            scanExpr(moe.subject(), gm);
        } else if (e instanceof ArrayExpression ae) {
            ae.resultType.use(t -> collect(t, gm));
            for (var elem : ae.elements()) scanExpr(elem, gm);
        } else if (e instanceof BinaryExpression be) {
            scanExpr(be.left(), gm);
            scanExpr(be.right(), gm);
        } else if (e instanceof UnaryExpression ue) {
            scanExpr(ue.operand(), gm);
        } else if (e instanceof BlockExpression be) {
            for (var s : be.block()) scanStmt(s, gm);
            scanExpr(be.result(), gm);
        }
    }

    /**
     * Apply the ambient generic substitution and, if the result is a concrete
     * mono item (generic class/interface/array/tuple/named prototype), queue it.
     */
    private void collect(TypeDeclarer td, GenericMap gm) {
        td = gm.mapIf(td);
        if (td.hasTypeVar()) return;
        switch (td) {
            case DerivedTypeDeclarer dtd -> {
                if (!dtd.derivedType().generic().isEmpty()) {
                    // 跨模块泛型类型：先登记公共合成模块（懒创建 + 重建模块 DAG），
                    // 否则 concretizeClass 时 owner() 找不到归属模块。
                    ensureGenericModule(dtd);
                    // 已具体化不再入队：concretizeClass 的 collect 会反复遇到
                    // 同一具体化类型（如 Pair`int, *?int`），否则 pending 永不
                    // 排空（generic-1 死循环 guard 到 20 万）。
                    // 注意用 concretized()（owner 感知）：跨模块泛型实例写入
                    // 定义所属模块的 map，this 的 map 会永远 miss → 死循环。
                    if (!concretized(Mangle.symbol(dtd.derivedType()))) {
                        pending.add(dtd);
                    }
                }
            }
            case ArrayTypeDeclarer atd -> {
                if (!concretized(Mangle.symbol(atd))) pending.add(atd);
            }
            case TupleTypeDeclarer ttd -> {
                // 跨模块元组：先登记公共合成模块（懒创建 + 重建模块 DAG），
                // 否则 concretizeTuple 时 owner() 找不到归属模块。
                ensureTupleModule(ttd);
                if (!concretized(Mangle.symbol(ttd))) pending.add(ttd);
            }
            case NamedFuncTypeDeclarer nftd -> {
                if (nftd.def().has() && !nftd.derivedType().generic().isEmpty()
                        && !concretized(Mangle.symbol(nftd.derivedType()))) {
                    pending.add(nftd);
                }
            }
            case AnonFuncTypeDeclarer aftd -> {
                // mapIf(FuncTypeDeclarer) 会把函数类型具体化为 AnonFuncTypeDeclarer
                // （instantiate 后无类型变量）；其原型 typedef（Feng$Proto_<key>）
                // 后端需要，必须 concretize 成 PrototypeDefinition 进依赖图。
                if (!aftd.prototype().hasTypeVar()) {
                    var sym = Mangle.anonProtoSymbol(aftd.prototype());
                    if (!concretized(sym)) pending.add(aftd);
                }
            }
            default -> {
            }
        }
    }

    /**
     * 元组元素跨多个模块时，先向 {@link ModuleManager} 登记公共合成模块：
     * 懒创建空合成模块、登记使用模块对它的依赖，并重建模块 DAG。这样
     * {@code concretizeTuple} 时 {@code owner(symbol)} 能按定义站点归属找到它。
     */
    private void ensureTupleModule(TupleTypeDeclarer ttd) {
        if (manager == null) return; // 单文件模式：无跨模块元组
        var mods = Mangle.elementModules(ttd);
        if (mods.size() <= 1) return; // 单模块或全内置：无需合成模块
        var user = table.module.has() ? table.module.must().path() : null;
        manager.ensureSynthetic(mods, user);
    }

    /**
     * 泛型类型（类/接口/结构）跨多个模块时，先向 {@link ModuleManager} 登记
     * 公共合成模块，与 {@link #ensureTupleModule} 同机制。类型实参若自身也是
     * 跨模块泛型/元组，先递归登记其合成模块，保证合成模块之间的依赖边正确。
     */
    private void ensureGenericModule(DerivedTypeDeclarer dtd) {
        if (manager == null) return; // 单文件模式：无跨模块
        var dt = dtd.derivedType();
        // 先递归登记实参里的跨模块泛型/元组，让内层合成模块先建、可被 import。
        for (var a : dt.generic()) {
            ensureArgModule(a);
        }
        var mods = Mangle.genericModules(dt.symbol(), dt.generic());
        if (mods.size() <= 1) return; // 单模块或全内置：无需合成模块
        // 存在最小公共后代（通常就是使用模块自己）：类型直接归它（真实模块），
        // 不建合成模块，从而避免「合成模块 ↔ 使用模块」的镜像环。
        if (manager.commonDescendant(mods) != null) return;
        var user = table.module.has() ? table.module.must().path() : null;
        manager.ensureSynthetic(mods, user);
    }

    /** 递归登记类型实参里涉及的跨模块泛型/元组合成模块。 */
    private void ensureArgModule(TypeDeclarer td) {
        if (td instanceof DerivedTypeDeclarer dtd) {
            if (!dtd.derivedType().generic().isEmpty()) {
                ensureGenericModule(dtd);
            }
        } else if (td instanceof TupleTypeDeclarer ttd) {
            ensureTupleModule(ttd);
            for (var e : ttd.elements()) ensureArgModule(e);
        } else if (td instanceof ArrayTypeDeclarer atd) {
            ensureArgModule(atd.element());
        }
    }

    // ---- M2: concretization ----

    private void concretizeTypes() {
        while (!pending.isEmpty()) {
            var td = pending.poll();
            var def = concretizeType(td);
            if (def != null) {
                var o = owner(def.symbol());
                if (o.concretized.add(def)) {
                    o.concreteBySymbol.put(def.symbol(), def);
                }
            }
        }
    }

    private TypeDefinition concretizeType(TypeDeclarer td) {
        return switch (td) {
            case DerivedTypeDeclarer dtd -> concretizeDerived(dtd);
            case ArrayTypeDeclarer atd -> concretizeArray(atd);
            case TupleTypeDeclarer ttd -> concretizeTuple(ttd);
            case NamedFuncTypeDeclarer nftd -> concretizePrototype(nftd);
            case AnonFuncTypeDeclarer aftd -> concretizeAnonPrototype(aftd);
            default -> null;
        };
    }

    /**
     * 匿名函数类型 → 命名 PrototypeDefinition：symbol = {@code Proto_<protoKey>}
     * （module=FENG，与 TypeWriter 的 {@code Feng$Proto_<key>} typedef 一致），
     * prototype 已具体化（无类型变量）。
     */
    private TypeDefinition concretizeAnonPrototype(AnonFuncTypeDeclarer aftd) {
        var pt = aftd.prototype();
        var symbol = Mangle.anonProtoSymbol(pt);
        return new PrototypeDefinition(pt.pos(), Modifier.empty(), symbol,
                TypeParameters.empty(), pt);
    }

    private TypeDefinition concretizeDerived(DerivedTypeDeclarer dtd) {
        var def = dtd.derivedType().def();
        if (def instanceof ClassDefinition cd) {
            return concretizeClass(cd, dtd.derivedType().generic());
        }
        if (def instanceof InterfaceDefinition id) {
            return concretizeInterface(id, dtd.derivedType().generic());
        }
        return null;
    }

    /**
     * collect 已实例化方法原型的参数/返回类型（值类型如元组、定长数组、
     * 命名原型等），确保其 typedef 被物化。concretizeClass/concretizeInterface
     * 实例化方法原型后必须调用——否则方法返回元组（如 {@code Cache`int,int`.get}
     * 的 {@code (int,bool)}）不进入 pending，跨模块类方法又不会被
     * discoverMethodInsts 的 Seed 3 扫到（该 Seed 只遍历 {@code this.concretized}），
     * 导致 {@code Feng$Tuple_Int_Bool} typedef 缺失。
     */
    private void collectProto(Prototype pt) {
        for (var p : pt.parameterSet()) {
            if (p instanceof FixedParameter fp) collect(fp.type(), GenericMap.EMPTY);
        }
        pt.returnSet().use(t -> collect(t, GenericMap.EMPTY));
    }

    /**
     * Concrete generic class: rebuild with an empty generic, mangle symbol and
     * every field/method/inherit/impl substituted through {@link GenericMap}.
     * Inherited members are already mapped onto the template's parameter space
     * by {@code checkInherit}, so a single further {@code gm.mapIf} suffices.
     */
    private ClassDefinition concretizeClass(ClassDefinition cd, TypeArguments args) {
        var gm = GenericMap.make(cd, cd.generic(), args);

        var symbol = Mangle.symbol(cd.link(args));
        owner(symbol).classGmBySymbol.put(symbol, gm);

        var inherit = cd.inherit().map(gm::mapIf);
        inherit.use(dt -> collect(new DerivedTypeDeclarer(dt.pos(), dt), GenericMap.EMPTY));

        var impl = new SymbolMap<DerivedType>();
        cd.impl().each((sym, dt) -> {
            var idt = gm.mapIf(dt);
            collect(new DerivedTypeDeclarer(idt.pos(), idt), GenericMap.EMPTY);
            impl.add(sym, idt);
        });

        var fields = new IdentifierMap<ClassField>();
        cd.fields().each((name, f) -> {
            var ft = gm.mapIf(f.type());
            collect(ft, GenericMap.EMPTY);
            fields.add(name, new ClassField(f.pos(), f.modifier(), f.declare(),
                    f.name(), ft));
        });

        var methods = new IdentifierMap<ClassMethod>();
        cd.methods().each((name, m) -> {
            var pt = gm.instantiate(m.prototype());
            // 实例化后原型里的参数/返回类型可能是新的具体 mono 类型（如返回
            // 元组），必须 collect 进 pending，否则其 typedef 缺失。
            collectProto(pt);
            // 方法体必须按 gm 具体化（与 concretizeFuncs 同款）：否则残留
            // 模板类型（如 BigBox`V`.change 里 `var o V` 仍是 $V / BigBox_V）。
            var proc = m.procedure().map(p -> new Procedure(p.pos(), pt,
                    p.body().mono(gm), p.labels()));
            var cm = new ClassMethod(m.pos(), m.modifier(), m.name(), m.generic(),
                    m.escaped(), m.unmodifiable(), pt, proc, m.returnThis());
            cm.dynamic(m.dynamic());
            methods.add(name, cm);
        });

        var concrete = new ClassDefinition(cd.pos(), cd.modifier(), symbol,
                TypeParameters.empty(), cd.isFinal(),
                inherit, impl, fields, methods, cd.macros());

        // 拷贝运算符/索引宏到具体化类：ClassMetadata.collectMethods 从
        // binaryOperators/unaryOperators/indexOperator 收方法，漏拷会让
        // 具体化类缺 `feng$macro$index$get/set`（index-override-2 报
        // Vector_A$...index$get 未声明）。
        cd.resourceFree().use(m ->
                concrete.resourceFree().set(instantiateMacro(m, gm)));
        cd.binaryOperators().forEach((op, m) ->
                concrete.binaryOperators().put(op, instantiateMacro(m, gm)));
        cd.unaryOperators().forEach((op, m) ->
                concrete.unaryOperators().put(op, instantiateMacro(m, gm)));
        cd.indexOperator().use(io -> concrete.indexOperator().set(new IndexOperator(
                io.get().map(m -> instantiateMacro(m, gm)),
                io.set().map(m -> instantiateMacro(m, gm)))));

        // Rebuild analysis caches: allFields/allMethods (direct + inherited),
        // inheriting members already expressed in the template's parameter space.
        cd.inheritFields().each((name, f) ->
                concrete.allFields().add(name, new ClassField(f.pos(), f.modifier(), f.declare(),
                        f.name(), gm.mapIf(f.type()))));
        concrete.allFields().addAll(fields);
        cd.inheritMethods().each((name, m) -> {
            var pt = gm.instantiate(m.prototype());
            collectProto(pt);
            var proc = m.procedure().map(p -> new Procedure(p.pos(), pt,
                    p.body().mono(gm), p.labels()));
            var cm = new ClassMethod(m.pos(), m.modifier(), m.name(),
                    m.generic(), m.escaped(), m.unmodifiable(), pt, proc, m.returnThis());
            cm.dynamic(m.dynamic());
            concrete.allMethods().add(name, cm);
        });
        concrete.allMethods().addAll(methods);

        // Back-pointers.
        concrete.fields().each(f -> f.master(concrete));
        concrete.allFields().each(f -> f.master(concrete));
        concrete.methods().each(m -> m.master(concrete));
        concrete.allMethods().each(m -> m.master(concrete));

        assertGenericClear(concrete, gm);
        return concrete;
    }

    /**
     * 运算符/索引宏方法的 gm 实例化：prototype 按 gm 替换（方法体与普通方法
     * 同款——索引宏 `values[index]` 等表达式须具体化，否则残留模板类型）。
     */
    private static ClassMethod instantiateMacro(ClassMethod m, GenericMap gm) {
        var pt = gm.instantiate(m.prototype());
        var proc = m.procedure().map(p -> new Procedure(p.pos(), pt,
                p.body().mono(gm), p.labels()));
        var cm = new ClassMethod(m.pos(), m.modifier(), m.name(), m.generic(),
                m.escaped(), m.unmodifiable(), pt, proc, m.returnThis());
        cm.dynamic(m.dynamic());
        return cm;
    }

    private InterfaceDefinition concretizeInterface(InterfaceDefinition id, TypeArguments args) {
        var gm = GenericMap.make(id, id.generic(), args);

        var symbol = Mangle.symbol(id.link(args));

        var methods = new IdentifierMap<InterfaceMethod>();
        id.methods().each((name, m) -> {
            var pt = gm.instantiate(m.prototype());
            collectProto(pt);
            methods.add(name, new InterfaceMethod(m.pos(), m.modifier(), m.name(), m.generic(),
                    m.escaped(), m.unmodifiable(), pt, m.returnThis()));
        });

        var parts = new SymbolMap<DerivedType>();
        id.parts().each((sym, dt) -> {
            var pdt = gm.mapIf(dt);
            collect(new DerivedTypeDeclarer(pdt.pos(), pdt), GenericMap.EMPTY);
            parts.add(sym, pdt);
        });

        var concrete = new InterfaceDefinition(id.pos(), id.modifier(), symbol,
                TypeParameters.empty(), methods, parts, id.macros());

        concrete.allMethods().addAll(methods);
        id.allMethods().each((name, m) -> {
            if (!concrete.allMethods().exists(name)) {
                var pt = gm.instantiate(m.prototype());
                collectProto(pt);
                concrete.allMethods().add(name, new InterfaceMethod(m.pos(), m.modifier(), m.name(),
                        m.generic(), m.escaped(), m.unmodifiable(), pt, m.returnThis()));
            }
        });

        return concrete;
    }

    /**
     * Concrete array: build a value-type definition whose element type is
     * already filled in, eliminating the elementParam + typeMap indirection.
     */
    private TypeDefinition concretizeArray(ArrayTypeDeclarer atd) {
        if (ArrayTypeDeclarer.isByteArray(atd) && atd.refer().has()) return null;
        collect(atd.element(), GenericMap.EMPTY);
        var symbol = Mangle.symbol(atd);
        if (atd.refer().has()) {
            boolean phantom = atd.refer().get().kind() == ReferKind.PHANTOM;
            return new ArrayRefDefinition(symbol, atd.element(), phantom);
        }
        return new FixedArrayDefinition(symbol, atd.element(), atd.len().intValue());
    }

    private TypeDefinition concretizeTuple(TupleTypeDeclarer ttd) {
        for (var et : ttd.elements()) {
            collect(et, GenericMap.EMPTY);
        }
        var symbol = Mangle.symbol(ttd);
        return new TupleDefinition(symbol, ttd.elements());
    }

    private TypeDefinition concretizePrototype(NamedFuncTypeDeclarer nftd) {
        var pd = nftd.def().get().must();
        var dt = nftd.derivedType();
        var gm = GenericMap.make(pd, pd.generic(), dt.generic());
        var symbol = Mangle.symbol(dt);
        var concrete = new PrototypeDefinition(pd.pos(), pd.modifier(), symbol,
                TypeParameters.empty(), gm.instantiate(pd.prototype()));
        return concrete;
    }

    /**
     * After substitution, no {@link GenericTypeDeclarer} should remain from
     * class-level parameters; any survivor must be a method-level generic.
     */
    private void assertGenericClear(ClassDefinition concrete, GenericMap gm) {
        // Lightweight sanity check only over field types (method-level generics
        // legitimately retain their own parameters).
        for (var f : concrete.fields()) {
            if (f.type().hasTypeVar()) {
                throw new IllegalStateException(
                        "unresolved type variable in concrete field: " + f);
            }
        }
    }

    // ---- M3: type initialization dependency DAG ----

    /**
     * Value-type (embedding) dependencies of a type definition. References and
     * reference arrays do not require the target to be defined first.
     */
    private List<TypeDefinition> initDeps(TypeDefinition def) {
        var deps = new ArrayList<TypeDefinition>();
        if (def instanceof ClassDefinition cd) {
            cd.inherit().use(dt -> addDef(deps, resolveDerived(dt)));
            for (var f : cd.fields()) {
                collectValueDeps(f.type(), deps);
            }
            for (var m : cd.methods()) {
                collectProtoDeps(m.prototype(), deps);
            }
        } else if (def instanceof InterfaceDefinition id) {
            for (var p : id.parts().values()) {
                addDef(deps, resolveDerived(p));
            }
            for (var m : id.methods()) {
                collectProtoDeps(m.prototype(), deps);
            }
        } else if (def instanceof StructureDefinition sd) {
            for (var f : sd.fields()) {
                collectValueDeps(f.type(), deps);
            }
        } else if (def instanceof FixedArrayDefinition fad) {
            collectValueDeps(fad.elementType(), deps);
        } else if (def instanceof ArrayRefDefinition ard) {
            // SRef/PRef 数组 struct = { T* $values; Int64 $length; }，元素永远
            // 躲在指针后面，只需 tag 前向（declareConcreteStructForwards 已为
            // 所有物化类型发射 typedef 前向），不产生任何定义级依赖。
        } else if (def instanceof TupleDefinition td) {
            if (td.elementTypes() != null) {
                for (var et : td.elementTypes()) {
                    collectValueDeps(et, deps);
                }
            }
        }
        return deps;
    }

    /**
     * Resolve a DerivedType reference to its (mono or non-generic) definition.
     */
    private TypeDefinition resolveDerived(DerivedType dt) {
        if (dt.generic().isEmpty()) return dt.def();
        return owner(dt.symbol()).concreteBySymbol.get(Mangle.symbol(dt));
    }

    /**
     * Collect definition-level dependencies of a concrete mono type's
     * <em>value-embedded</em> members. 前端 {@code getClassTypeField} 已按值
     * 类型（定长数组、元组、值类字段）递归建好类间 DAG；这里只补泛型物化后
     * 的具体类型依赖，并遵守同一套 C 嵌入分级：
     * <ul>
     *   <li>数组 typedef 本身按值嵌入消费者，必须计入；</li>
     *   <li>定长数组的元素按值嵌入（{@code {T $values[N];}}）→ 递归；</li>
     *   <li>引用（{@code *?A}）与 SRef/PRef 数组的元素都躲在指针
     *       （{@code T* $values}）后面 → tag 级，不建边（Fix B 已为所有物化
     *       类型发射 typedef 前向，嵌套数组引用也只需前向）。</li>
     * </ul>
     */
    private void collectValueDeps(TypeDeclarer td, List<TypeDefinition> out) {
        if (td instanceof ArrayTypeDeclarer atd) {
            // 数组 typedef 本身必须先进依赖图：定长/SRef/PRef 数组结构体
            // （{ T $values[N]; } / { T* $values; Int64 $length; }）都按值嵌入
            // 类/结构体字段，字段 struct 定义依赖其 typedef。
            addDef(out, concreteDef(Mangle.symbol(atd)));
            if (atd.refer().none()) {
                // 定长数组按值嵌入元素：元素须先完整定义。
                collectValueDeps(atd.element(), out);
            }
            // SRef/PRef：元素躲在 T* $values 指针后，tag 级即可——不建边。
            return;
        }
        if (td instanceof DerivedTypeDeclarer dtd) {
            // 引用（指针）目标躲在指针后，tag 级即可；按值嵌入才需完整定义。
            if (dtd.refer().none()) {
                addDef(out, resolveDerived(dtd.derivedType()));
            }
        } else if (td instanceof FuncTypeDeclarer ftd) {
            // 函数类型字段按值嵌入函数指针 typedef（Feng$Proto_<key>），其 typedef
            // 必须先于消费类/结构体定义。
            addDef(out, funcProtoDef(ftd));
        } else if (td instanceof TupleTypeDeclarer ttd) {
            // 元组是值类型：元组节点本身必须先于结构体定义；
            // 元素经上述分支分级——指针元素自动跳过，仅值元素产生依赖。
            addDef(out, concreteDef(Mangle.symbol(ttd)));
            for (var et : ttd.elements()) collectValueDeps(et, out);
        }
    }

    /** 函数类型（匿名/命名）具体化后的 PrototypeDefinition。 */
    private TypeDefinition funcProtoDef(FuncTypeDeclarer ftd) {
        if (ftd instanceof NamedFuncTypeDeclarer nftd) {
            return concreteDef(Mangle.symbol(nftd.derivedType()));
        }
        return concreteDef(Mangle.anonProtoSymbol(ftd.prototype()));
    }

    /**
     * Collect value-type dependencies of a method prototype (parameter types
     * and return type). 方法槽签名发射会引用这些类型（参数/返回值按值传递的
     * 定长/SRef/PRef 数组、元组、值类/结构体），其 typedef 必须先于类/接口的
     * meta 结构体。
     */
    private void collectProtoDeps(Prototype pt, List<TypeDefinition> out) {
        for (var p : pt.parameterSet()) {
            if (p instanceof FixedParameter fp) collectValueDeps(fp.type(), out);
        }
        pt.returnSet().use(t -> collectValueDeps(t, out));
    }

    private void addDef(List<TypeDefinition> out, TypeDefinition def) {
        if (def != null && !out.contains(def)) out.add(def);
    }

    // ---- M4: function / method concretization ----

    /**
     * Register a concrete generic function instantiation discovered at a call
     * site, after resolving its type arguments through {@code gm}.
     */
    private void registerFuncInstantiation(Symbol sym, TypeArguments genericArgs,
                                           GenericMap gm) {
        var resolved = gm.mapAll(genericArgs);
        if (resolved.hasTypeVar()) return;
        var fd = findFuncDef(sym);
        if (fd != null) {
            funcInsts.add(new FuncInstantiation(fd, resolved));
        }
    }

    /**
     * Register a concrete method-level generic instantiation discovered at a
     * call site. Walks up the inheritance chain to find the class that actually
     * declares the method, mirroring {@code CGenerator}'s owner lookup.
     */
    private void registerMethodInst(ClassDefinition cd, DerivedType classDt,
                                    MethodExpression me, TypeArguments resolvedGeneric) {
        var owner = cd;
        while (!owner.methods().exists(me.method().name()) && owner.parent().has()
                && owner.parent().must() != ClassDefinition.ObjectClass) {
            owner = owner.parent().must();
        }
        var cm = owner.methods().tryGet(me.method().name());
        if (!cm.has()) return;                       // interface method / operator macro
        var ownerDt = (owner == cd) ? classDt : ancestorDt(cd, classDt, owner);
        methodInsts.add(new MethodInstantiation(ownerDt, cm.get(), resolvedGeneric));
    }

    /**
     * Compute the concrete {@link DerivedType} of an ancestor {@code anc} of
     * {@code cd} given {@code cd}'s own concrete type {@code dt}. Walks the
     * inheritance chain, mapping each level's type args through the child's
     * class-level {@link GenericMap}.
     */
    private DerivedType ancestorDt(ClassDefinition cd, DerivedType dt, ClassDefinition anc) {
        if (anc == cd) return dt;
        if (anc.generic().isEmpty()) {
            return anc.link();
        }
        var cur = cd;
        var curDt = dt;
        while (cur != anc) {
            var inherit = cur.inherit().must();
            var gm = GenericMap.make(cur, cur.generic(), curDt.generic());
            var parentArgs = gm.mapAll(inherit.generic());
            cur = cur.parent().must();
            curDt = new DerivedType(inherit.pos(), inherit.symbol(), parentArgs);
            curDt.def(cur);
            curDt.gm(GenericMap.make(cur, cur.generic(), parentArgs));
        }
        return curDt;
    }

    /**
     * Transitively discover concrete types and further function instantiations
     * inside generic function bodies (worklist BFS, mirrors the old pass).
     */
    private void discoverFuncInsts() {
        var processed = new HashSet<FuncInstantiation>();
        var worklist = new ArrayList<>(funcInsts);
        int i = 0;
        while (i < worklist.size()) {
            var fi = worklist.get(i++);
            if (fi.args().hasTypeVar()) continue;
            if (!processed.add(fi)) continue;
            var gm = GenericMap.make(fi.fd(), fi.fd().generic(), fi.args());
            scanFunc(fi.fd(), gm);
            for (var fi2 : funcInsts) {
                if (!processed.contains(fi2)) worklist.add(fi2);
            }
        }
    }

    /**
     * Produce concrete FunctionDefinitions (no generic parameters, prototype
     * instantiated, body rewritten through {@code mono(gm)}) into
     * {@code ast.monoFuncs}.
     */
    private void concretizeFuncs() {
        for (var fi : funcInsts) {
            if (fi.args().hasTypeVar()) continue;
            var fd = fi.fd();
            var gm = GenericMap.make(fd, fd.generic(), fi.args());
            var symbol = Mangle.symbol(fd.symbol(), fi.args());
            var prototype = gm.instantiate(fd.prototype());
            var o = owner(symbol);
            if (fd.procedure().has()) {
                var proc = fd.procedure().must();
                var nproc = new Procedure(proc.pos(), prototype,
                        proc.body().mono(gm), proc.labels());
                // 重建后把 body 内 return/throw 的 procedure 重指向新 proc：
                // 后端按它取返回类型发射（指向模板 proc 会残留 [*]T 类型变量）。
                retarget(nproc, nproc.body());
                o.table.monoFuncs.add(new FunctionDefinition(fd.pos(), fd.modifier(),
                        symbol, TypeParameters.empty(), nproc));
            } else {
                o.table.monoFuncs.add(new FunctionDefinition(fd.pos(), fd.modifier(),
                        symbol, TypeParameters.empty(), prototype));
            }
        }
    }

    /**
     * 把语句树内全部 {@code ReturnStatement}/{@code ThrowStatement} 的
     * {@code procedure()} 重指向 {@code nproc}（mono 重建时它们仍指向模板
     * Procedure，后端发射 return 的返回类型时残留类型变量）。
     */
    private static void retarget(Procedure nproc, Statement s) {
        if (s instanceof ReturnStatement rs) {
            rs.procedure().set(nproc);
            return;
        }
        if (s instanceof ThrowStatement ts) {
            ts.procedure().set(nproc);
            return;
        }
        if (s instanceof BlockStatement bs) {
            for (var x : bs.list()) retarget(nproc, x);
            return;
        }
        if (s instanceof IfStatement is) {
            is.init().use(x -> retarget(nproc, x));
            retarget(nproc, is.yes());
            is.not().use(x -> retarget(nproc, x));
            return;
        }
        if (s instanceof ConditionalForStatement cfs) {
            cfs.initializer().use(x -> retarget(nproc, x));
            retarget(nproc, cfs.body());
            cfs.updater().use(x -> retarget(nproc, x));
            return;
        }
        if (s instanceof IterableForStatement ifs) {
            retarget(nproc, ifs.body());
            return;
        }
        if (s instanceof SwitchStatement ss) {
            ss.init().use(x -> retarget(nproc, x));
            for (var br : ss.branches()) retarget(nproc, br);
            ss.defaultBranch().use(x -> retarget(nproc, x));
            return;
        }
        if (s instanceof TryStatement ts) {
            retarget(nproc, ts.body());
            for (var cc : ts.catchClauses()) retarget(nproc, cc.body());
            ts.finallyClause().use(x -> retarget(nproc, x));
            return;
        }
        if (s instanceof LabeledStatement ls) {
            retarget(nproc, ls.target());
            return;
        }
        if (s instanceof Branch br) {
            retarget(nproc, br.body());
            return;
        }
        if (s instanceof DeclarationStatement ds) {
            for (var v : ds.variables()) {
                v.value().use(e -> retargetExpr(nproc, e));
            }
            return;
        }
        if (s instanceof CallStatement cs) {
            retargetExpr(nproc, cs.call());
        }
    }

    /**
     * 表达式内嵌语句（块表达式）里也可能有 return/throw。
     */
    private static void retargetExpr(Procedure nproc, Expression e) {
        if (e instanceof BlockExpression be) {
            for (var s : be.block()) retarget(nproc, s);
            retargetExpr(nproc, be.result());
            return;
        }
        if (e instanceof CallExpression ce) {
            retargetExpr(nproc, ce.callee());
            for (var a : ce.arguments()) retargetExpr(nproc, a);
        }
    }

    // ---- M4: method-level generic concretization ----

    /**
     * Concretize one method-level generic instantiation into a concrete
     * {@link ClassMethod}: empty method generic, mangled {@code name_Arg...},
     * class + method {@link GenericMap} overlaid, prototype + body fully
     * substituted, {@code master} pointing at the concrete class.
     */
    private ClassMethod concretizeMethod(MethodInstantiation mi) {
        var classDt = mi.classDt();
        var cm = mi.method();
        var methodArgs = mi.methodArgs();
        var cd = (ClassDefinition) classDt.def();

        var classGm = GenericMap.make(cd, cd.generic(), classDt.generic());
        var gm = GenericMap.make(cm, classGm, cm.generic(), methodArgs);

        var concreteClass = classDt.generic().isEmpty()
                ? cd
                : (ClassDefinition) owner(classDt.symbol())
                .concreteBySymbol.get(Mangle.symbol(classDt));
        if (concreteClass == null) {
            // Ancestor owner not concretized yet (inherited method-level generic):
            // concretize on demand so `master` points at a real concrete class.
            concreteClass = (ClassDefinition) concretizeDerived(
                    new DerivedTypeDeclarer(classDt.pos(), classDt));
            var o = owner(concreteClass.symbol());
            if (o.concretized.add(concreteClass)) {
                o.concreteBySymbol.put(concreteClass.symbol(), concreteClass);
            }
        }

        var name = new Identifier(cm.name().value() + "_" +
                methodArgs.stream().map(Mangle::typeKey)
                        .collect(Collectors.joining("_")));
        var prototype = gm.instantiate(cm.prototype());
        var proc = cm.procedure().must();
        var concrete = new ClassMethod(cm.pos(), cm.modifier(), name,
                TypeParameters.empty(), cm.escaped(), cm.unmodifiable(),
                prototype, Optional.of(new Procedure(proc.pos(), prototype,
                proc.body().mono(gm), proc.labels())),
                cm.returnThis());
        concrete.dynamic(false);  // 方法级泛型实例化不参与动态派发（直调）
        concrete.master(concreteClass);
        owner(concreteClass.symbol()).table.monoMethods
                .computeIfAbsent(cd.symbol(), s -> new ArrayList<>())
                .add(concrete);
        return concrete;
    }

    /**
     * Discover and concretize method-level generic instantiations. Seeds from
     * concrete function bodies, non-generic class method bodies and concrete
     * class method bodies (with the class-level {@link GenericMap}), then
     * BFS-concretizes each instantiation and rescans its body for transitive
     * method-level calls.
     */
    private void discoverMethodInsts() {
        // Seed 1: concrete function bodies (already rewritten, no type vars).
        for (var fd : table.monoFuncs) {
            scanFunc(fd, GenericMap.EMPTY);
        }
        // Seed 2: non-generic class method bodies.
        for (var cd : table.dagClasses) {
            if (cd.generic().isEmpty()) {
                for (var cm : cd.methods()) scanMethod(cm, GenericMap.EMPTY);
            }
        }
        // Seed 3: concrete class method bodies (class-level type vars survive).
        for (var def : concretized) {
            if (def instanceof ClassDefinition cd) {
                var gm = classGmBySymbol.getOrDefault(cd.symbol(), GenericMap.EMPTY);
                for (var cm : cd.methods()) scanMethod(cm, gm);
            }
        }

        // BFS: concretize, then rescan the concrete body for transitive calls.
        var processed = new HashSet<MethodInstantiation>();
        var worklist = new ArrayList<>(methodInsts);
        int i = 0;
        while (i < worklist.size()) {
            var mi = worklist.get(i++);
            if (mi.hasTypeVar()) continue;
            if (!processed.add(mi)) continue;
            var concrete = concretizeMethod(mi);
            scanMethod(concrete, GenericMap.EMPTY);
            for (var mi2 : methodInsts) {
                if (!processed.contains(mi2) && !worklist.contains(mi2)) {
                    worklist.add(mi2);
                }
            }
        }
    }

    // ---- M5: anchor-after ordering ----

    /**
     * 锚点后置：每个 mono 类型放到「它依赖的类型」之后。
     * <p>前端已按依赖排好 dagXxx + 全局变量，模块也有序；因此只需给每个 mono
     * 类型找到「其具体化后依赖中生成序最靠后」的锚点，插到它后面，全局顺序即
     * 不乱。依赖可能是原始类型（进 {@code orderIndex}）或另一个 mono 类型（锚点
     * 递归向上传播）。按依赖递归（后序）自然排好 mono→mono，无需对子图再做
     * DAG 排序；结果收集进有序 {@link #monoAfter}（LinkedHashMap）/ {@link #monoHead}。
     */
    private void buildUnitDeps() {
        // 原始类型生成序索引：与 TypeWriter 实际生成顺序一致
        // （结构体 → 原型 → 类 → 接口；泛型/builtin 不生成，不进索引）。
        var orderIndex = new HashMap<TypeDefinition, Integer>();
        var idx = 0;
        for (var sd : table.dagStructures) orderIndex.put(sd, idx++);
        for (var pd : table.dagPrototypes) {
            if (pd.prototype().hasTypeVar()) continue;
            orderIndex.put(pd, idx++);
        }
        for (var cd : table.dagClasses) {
            if (cd.generic().isEmpty() && !cd.builtin()) orderIndex.put(cd, idx++);
        }
        for (var id : table.dagInterfaces) {
            if (id.generic().isEmpty() && !id.builtin()) orderIndex.put(id, idx++);
        }

        var monos = new HashSet<>(concretized);
        table.monoAfter.clear();
        table.monoHead.clear();

        // 后序递归：依赖先算锚点、先挂组，组内自然有序
        var anchor = new HashMap<TypeDefinition, Entity>();
        var visiting = new HashSet<TypeDefinition>();
        for (var m : concretized) {
            resolveAnchor(m, orderIndex, monos, anchor, visiting);
        }
    }

    /**
     * 递归计算 {@code m} 的锚点并挂到对应组（依赖先挂，故组内有序）。
     */
    private void resolveAnchor(TypeDefinition m,
                               Map<TypeDefinition, Integer> orderIndex,
                               Set<TypeDefinition> monos,
                               Map<TypeDefinition, Entity> anchor,
                               Set<TypeDefinition> visiting) {
        if (anchor.containsKey(m)) return;
        if (!visiting.add(m)) return; // 环：前端已解决循环初始化，防御性跳过

        Entity best = null;
        var bestPos = -1;
        for (var dep : initDeps(m)) {
            if (dep == m) continue;
            var oi = orderIndex.get(dep);
            if (oi != null) {
                if (oi > bestPos) { bestPos = oi; best = dep; }
            } else if (monos.contains(dep)) {
                resolveAnchor(dep, orderIndex, monos, anchor, visiting);
                var a = anchor.get(dep);
                if (a != null) {
                    var p = orderIndex.get(a);
                    if (p > bestPos) { bestPos = p; best = a; }
                }
            }
            // 跨模块依赖 / primitive：不在本模块生成，忽略
        }

        visiting.remove(m);
        anchor.put(m, best);
        if (best == null) table.monoHead.add(m);
        else table.monoAfter.computeIfAbsent(best, k -> new ArrayList<>()).add(m);
    }

    // ---- helpers (used by later milestones) ----

    private FunctionDefinition findFuncDef(Symbol symbol) {
        for (var fd : table.functionList) {
            if (fd.symbol().equals(symbol)) return fd;
        }
        var o = owner(symbol);
        if (o != this) {
            for (var fd : o.table.functionList) {
                if (fd.symbol().equals(symbol)) return fd;
            }
        }
        return null;
    }
}
