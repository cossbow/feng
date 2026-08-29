package org.cossbow.feng.mod;

import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.analysis.mono.Monomorphization;
import org.cossbow.feng.ast.Mangle;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.dag.DAGUtil;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.util.DedupCache;
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.util.Optional;

import java.util.*;

/**
 * 模块图管理器：封装模块 DAG，并 lazy 创建/查找每个模块的
 * {@link Monomorphization} 实例。
 *
 * <p>构造时注入内置合成模块 {@link Mangle#FENG}（生成共享头 {@code Feng.h}），
 * 得到基础模块 DAG；单态化 pass 通过 {@link #mono(ModulePath)} 按定义站点
 * 归属查找（并首次访问时创建）各模块的 mono 实例，取代原先在
 * {@code ModuleAnalysis} 里预建的 {@code Map<ModulePath, Monomorphization>}。
 *
 * <p>跨模块元组（元素来自多个模块）归属到一个「公共合成模块」：使用模块
 * {@code c} 使用 {@code (a$A, b$B)} 时，由 {@link #ensureSynthetic} 懒创建
 * 合成模块 {@code a_b}（空 result，import 各元素模块），并让使用模块 import
 * 该合成模块——对应依赖边 {@code a->a_b, b->a_b, a_b->c}，随后重建模块 DAG。
 * 合成模块内只有元组 typedef（无原始类型锚点），自然落入 {@code monoHead}。
 */
public class ModuleManager {

    /**
     * 内置合成模块 FENG：承载 primitive/内置元素的数组/元组 typedef。
     */
    private final FModule feng;
    /**
     * 原始模块 DAG（不含 FENG、不含合成模块）。
     */
    private final DAGGraph<FModule> base;
    private final Map<ModulePath, FModule> byPath = new HashMap<>();
    private final Map<ModulePath, Monomorphization> monos = new LinkedHashMap<>();
    /**
     * 公共合成模块：path → 空 FModule（import 各元素模块）。
     */
    private final Map<ModulePath, FModule> synthetics = new LinkedHashMap<>();
    /**
     * 当前模块 DAG（FENG + 原始模块 + 合成模块）。
     */
    private DAGGraph<FModule> dag;

    public ModuleManager(DAGGraph<FModule> raw) {
        this.base = raw;
        this.feng = new FModule(Mangle.FENG, List.of(),
                new ParseSymbolTable(Optional.of(Mangle.FENG), new DedupCache<>()));
        for (var fm : raw) {
            byPath.put(fm.path(), fm);
        }
        byPath.put(Mangle.FENG, feng);
        rebuild();
    }

    public DAGGraph<FModule> dag() {
        return dag;
    }

    /**
     * 查找 {@code path} 模块的 mono 实例；首次访问时用该模块的语义分析结果
     * （{@code FModule.result}）创建并缓存。
     *
     * @return 对应模块的 mono 实例；{@code path} 不在图里时返回 {@code null}
     */
    public Monomorphization mono(ModulePath path) {
        var m = monos.get(path);
        if (m == null) {
            var fm = byPath.get(path);
            if (fm == null) return null;
            m = new Monomorphization(fm.result.must(), this);
            monos.put(path, m);
        }
        return m;
    }

    /**
     * 确保跨模块元组的公共合成模块存在（懒创建空模块），并让使用模块
     * {@code user} import 该合成模块。返回合成模块路径。
     *
     * @param elementModules 元组元素所属模块（去重）
     * @param user           使用该跨模块元组的模块（可为 {@code null}）
     */
    public ModulePath ensureSynthetic(Collection<ModulePath> elementModules,
                                      ModulePath user) {
        var path = Mangle.syntheticModule(elementModules);
        if (!byPath.containsKey(path)) {
            var fm = new FModule(path, new ArrayList<>(elementModules),
                    new ParseSymbolTable(Optional.of(path), new DedupCache<>()));
            var ast = AnalyseSymbolTable.empty();
            ast.module.set(fm);
            fm.result.set(ast);
            byPath.put(path, fm);
            synthetics.put(path, fm);
        }
        if (user != null && !user.equals(path)) {
            var userFm = byPath.get(user);
            if (userFm != null && !userFm.imports().contains(path)) {
                var imports = new ArrayList<>(userFm.imports());
                imports.add(path);
                userFm.imports(imports);
            }
        }
        rebuild();
        return path;
    }

    /**
     * 由基础 DAG + 合成模块 + imports 派生边重建模块 DAG。
     */
    private void rebuild() {
        var nodes = new HashSet<FModule>(base.size() + synthetics.size() + 1);
        nodes.add(feng);
        nodes.addAll(base.all());
        nodes.addAll(synthetics.values());

        var edges = new HashSet<Groups.G2<FModule, FModule>>();
        // FENG 是所有模块的依赖（head）。
        for (var fm : base) edges.add(Groups.g2(feng, fm));
        for (var fm : synthetics.values()) edges.add(Groups.g2(feng, fm));
        // imports 派生依赖边：fm import dep → 边 dep -> fm。
        for (var fm : nodes) {
            if (fm == feng) continue;
            for (var imp : fm.imports()) {
                var dep = byPath.get(imp);
                if (dep != null) edges.add(Groups.g2(dep, fm));
            }
        }
        dag = DAGUtil.make(nodes, edges);
    }
}
