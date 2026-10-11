package org.cossbow.feng.analysis.meta;

import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.Optional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 类元数据组装 pass：把「类的方法」降级为完整函数描述符 {@link MethodFunc}。
 *
 * <p>只读不改 AST，产出 {@link SymbolMap}{@code <ClassMeta>} 写入 {@code ast.classMetas}。
 *
 * <p>覆盖三类类定义：
 * <ol>
 *   <li>{@code dagClasses} 里的非泛型类（generic 空，builtin 除外）；</li>
 *   <li>{@code monoDepsByUnit} 里的具体泛型类实例（mono2 产物，generic 空、
 *       symbol 已 mangle）；</li>
 *   <li>{@code monoMethods} 里方法级泛型实例化所挂靠的具体类（通过
 *       {@code cm.master()} 关联，必要时补建）。</li>
 * </ol>
 *
 * <p>每个类的 methods 覆盖：{@code methods()}（含 resourceFree，generic 空的方法）、
 * 运算符宏（binary / unary）、索引宏（index get / set）。方法级泛型模板
 * （{@code cm.generic()} 非空）不进——它们走 {@code MethodInstantiation}，
 * 其实例化产物由 {@code monoMethods} 单独挂入。
 */
public final class ClassMetadata {
    private final AnalyseSymbolTable ast;

    public ClassMetadata(AnalyseSymbolTable ast) {
        this.ast = ast;
    }

    private void addClass(TypeDefinition def, Map<Symbol, ClassMeta> order) {
        if (def instanceof ClassDefinition cd
                && cd.generic().isEmpty() && !cd.builtin()) {
            order.put(cd.symbol(), new ClassMeta(cd));
        }
    }

    private void collectAnchor(Entity anchor, Map<Symbol, ClassMeta> order) {
        var list = ast.monoAfter.get(anchor);
        if (list == null) return;
        for (var def : list) addClass(def, order);
    }

    public void build() {
        var order = new LinkedHashMap<Symbol, ClassMeta>();

        // 锚点后置的生成序（与 TypeWriter 一致）：monoHead 最前 → 结构体/原型
        // 锚点组 → 非泛型类 + 类锚点组（交织，父先于子）→ 接口锚点组。
        for (var def : ast.monoHead) addClass(def, order);
        for (var sd : ast.dagStructures) collectAnchor(sd, order);
        for (var pd : ast.dagPrototypes) collectAnchor(pd, order);
        for (var cd : ast.dagClasses) {
            if (cd.generic().isEmpty() && !cd.builtin()) {
                order.put(cd.symbol(), new ClassMeta(cd));
            }
            collectAnchor(cd, order);
        }
        for (var id : ast.dagInterfaces) collectAnchor(id, order);

        // 3. 填 methods：普通方法 + 运算符 / 索引宏
        for (var meta : order.values()) {
            collectMethods(meta.def(), meta);
        }

        // 4. 方法级泛型实例化（monoMethods）挂到对应具体类
        for (var list : ast.monoMethods.values()) {
            for (var cm : list) {
                var master = cm.master();
                var meta = order.get(master.symbol());
                if (meta == null) {
                    // 具体类不在 dagClasses / monoDepsByUnit（如仅被方法体引用的
                    // 具体实例）——补建，保证方法级泛型实例化总能挂上。
                    meta = new ClassMeta(master);
                    collectMethods(master, meta);
                    order.put(master.symbol(), meta);
                    assert false : "不应该啊？";
                }
                // 同名实例化（同一具体化方法被重复注册）防重——否则
                // IdentifierMap.add 抛 duplicate（generic-3 的 map_Float）。
                if (!meta.methods().exists(cm.name())) {
                    meta.addMethod(methodFunc(meta, cm));
                }
            }
        }

        // 5. 填 allMethods：当前类可见的全部方法（含继承）→ MethodFunc，
        //    一律按「声明方法」的类构建。ExprWriter 查此表直调，不再沿父链
        //    解析 owner（声明类解析在此一次性完成）。
        for (var meta : order.values()) {
            collectAllMethods(meta.def(), meta, order);
        }

        // 6. 输出为 SymbolMap（按类符号索引）
        for (var meta : order.values()) {
            ast.classMetas.add(meta.def().symbol(), meta);
        }
    }

    public MethodFunc
    methodFunc(ClassMeta meta, ClassMethod cm) {
        return buildMethodFunc(meta.def(), cm, meta);
    }

    /**
     * 收集一个类的全部可派发方法到 MethodFunc：普通方法（generic 空，含
     * resourceFree）、运算符宏、索引宏。方法级泛型模板跳过。
     */
    private void collectMethods(ClassDefinition cd, ClassMeta meta) {
        for (var cm : cd.methods()) {
            if (!cm.generic().isEmpty()) continue; // 方法级泛型模板：跳过
            meta.addMethod(methodFunc(meta, cm));
        }
        for (var cm : cd.binaryOperators().values()) {
            meta.addMethod(methodFunc(meta, cm));
        }
        for (var cm : cd.unaryOperators().values()) {
            meta.addMethod(methodFunc(meta, cm));
        }
        cd.indexOperator().use(io -> {
            io.get().use(cm ->
                    meta.addMethod(methodFunc(meta, cm)));
            io.set().use(cm ->
                    meta.addMethod(methodFunc(meta, cm)));
        });
    }

    /**
     * 填充 {@link ClassMeta#allMethods}：当前类可见的全部方法 → {@link MethodFunc}。
     * <ol>
     *   <li>{@code cd.allMethods()}（本类声明 + 继承克隆，含方法级泛型模板）——
     *       按声明类构建（{@code symbol} = {@code <声明类>$<名>}）；</li>
     *   <li>运算符 / 索引宏——声明类即当前类（宏不继承）；</li>
     *   <li>{@code methods()} 里未覆盖的（mono 方法级实例化）直接并入。</li>
     * </ol>
     */
    private void collectAllMethods(ClassDefinition cd, ClassMeta meta,
                                   Map<Symbol, ClassMeta> order) {
        for (var cm : cd.allMethods()) {
            var mf = methodFuncOfDecl(cd, cm, order);
            if (mf != null) meta.addAllMethod(mf);
        }
        for (var cm : cd.binaryOperators().values()) {
            meta.addAllMethod(methodFunc(meta, cm));
        }
        for (var cm : cd.unaryOperators().values()) {
            meta.addAllMethod(methodFunc(meta, cm));
        }
        cd.indexOperator().use(io -> {
            io.get().use(cm ->
                    meta.addAllMethod(methodFunc(meta, cm)));
            io.set().use(cm ->
                    meta.addAllMethod(methodFunc(meta, cm)));
        });
        for (var mf : meta.methods()) {
            if (!meta.allMethods().exists(mf.name())) {
                meta.addAllMethod(mf);
            }
        }
    }

    /**
     * 按「声明方法」的类构建 {@link MethodFunc}：{@code symbol} =
     * {@code <声明类>$<名>}、self 参数类型 = 声明类、{@code master} = 声明类
     * meta。继承克隆（{@code cd.allMethods()} 里的 inheritMethods）没有函数体，
     * prototype / body 取声明类原方法（checkInherit 克隆只带 prototype）。
     * 返回 {@code null} 表示该方法是无函数体的内建方法（如 Exception.trace，
     * StmtWriter 专用路径发射，不进全方法表）。
     */
    private MethodFunc methodFuncOfDecl(
            ClassDefinition cd, ClassMethod cm,
            Map<Symbol, ClassMeta> order) {
        var dc = declareClass(cd, cm.name());
        var dm = metaOf(dc, order);
        var src = dc.methods().tryGet(cm.name()).getOrElse(cm);
        if (src.procedure().none()) return null;
        return buildMethodFunc(dc, src, dm);
    }

    /**
     * 声明方法 {@code name} 的类：沿 {@code inherit()} 链找第一个
     * {@code methods()} 命中的类。与 {@code concreteAncestorDt} 同链
     * （具体化类 parent() 不可靠，inherit() 链已链接具体化父类）；找不到
     * （链断裂）退回 {@code cd} 自身。
     */
    private ClassDefinition declareClass(ClassDefinition cd, Identifier name) {
        var cur = cd;
        while (cur != null && cur != ClassDefinition.ObjectClass) {
            if (cur.methods().exists(name)) return cur;
            if (cur.inherit().none()) break;
            var idt = cur.inherit().must();
            if (!(idt.def() instanceof ClassDefinition p)) break;
            cur = p;
        }
        return cd;
    }

    /**
     * {@code dc} 的 {@link ClassMeta}：不在构建序（模板类 / 跨模块类）时临时
     * 构造——仅作 MethodFunc 的 master 载体（self 类型 + 符号前缀），不进表。
     */
    private ClassMeta metaOf(ClassDefinition dc, Map<Symbol, ClassMeta> order) {
        var m = order.get(dc.symbol());
        return m != null ? m : new ClassMeta(dc);
    }

    /**
     * 用「声明类」{@code dc} 及其原方法 {@code cm} 构建 {@link MethodFunc}
     * （self 首参注入、符号公式与 {@link #methodFunc} 一致，仅 def 换为 dc）。
     */
    private MethodFunc buildMethodFunc(ClassDefinition dc, ClassMethod cm,
                                       ClassMeta master) {
        var self = new SelfParameter(Position.ZERO, dc);
        var pc = cm.procedure().must();
        var pt = cm.prototype();
        var ps = pt.parameterSet();
        var params = CommonUtil.concat(List.of(self), ps.params());
        ps = new ParameterSet(ps.pos(), params);
        pt = new Prototype(pt.pos(), ps, pt.returnSet(), pt.catchAll());
        pc = new Procedure(pc.pos(), pt, pc.body(), pc.labels());
        return new MethodFunc(cm.name(),
                ClassMeta.methodSymbol(dc, cm.name()),
                pt, Optional.of(pc), master, cm.dynamic());
    }
}
