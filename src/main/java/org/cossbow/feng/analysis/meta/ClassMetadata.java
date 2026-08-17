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

        // 5. 输出为 SymbolMap（按类符号索引）
        for (var meta : order.values()) {
            ast.classMetas.add(meta.def().symbol(), meta);
        }
    }

    public MethodFunc
    methodFunc(ClassMeta meta, ClassMethod cm) {
        var self = new SelfParameter(Position.ZERO, meta.def());
        var pc = cm.procedure().must();
        var pt = cm.prototype();
        var ps = pt.parameterSet();
        var params = CommonUtil.concat(List.of(self), ps.params());
        ps = new ParameterSet(ps.pos(), params);
        pt = new Prototype(pt.pos(), ps, pt.returnSet());
        pc = new Procedure(pc.pos(), pt, pc.body(), pc.labels());
        return new MethodFunc(cm.name(),
                ClassMeta.methodSymbol(meta.def(), cm.name()),
                pt, Optional.of(pc), meta, cm.dynamic());
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
}