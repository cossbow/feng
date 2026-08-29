package org.cossbow.feng.analysis;

import org.cossbow.feng.analysis.meta.ClassMeta;
import org.cossbow.feng.analysis.meta.VTable;
import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.EnumDefinition;
import org.cossbow.feng.ast.GlobalVariable;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.SymbolMap;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.mod.FModule;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.util.Lazy;

import java.util.*;

/**
 * The analysis symbol table is generated after syntax analysis
 * and is used as input for the generation of target code.
 */
public class AnalyseSymbolTable {
    public final Lazy<FModule> module = Lazy.nil();

    public List<TypeDefinition> typeList;
    public List<FunctionDefinition> functionList;

    public List<GlobalVariable> constVars;
    public DAGGraph<GlobalVariable> dagVars;
    public List<EnumDefinition> enumList;
    public DAGGraph<PrototypeDefinition> dagPrototypes;
    public DAGGraph<StructureDefinition> dagStructures;
    public DAGGraph<InterfaceDefinition> dagInterfaces;
    public DAGGraph<ClassDefinition> dagClasses;

    public Map<StringLiteral, StringLiteral> stringCache;
    public final Lazy<FunctionDefinition> main = Lazy.nil();

    /**
     * Whether test mode is enabled.
     */
    public boolean test;

    /**
     * Testcase name filter — empty means run all.
     */
    public Set<String> testFilter = Set.of();

    /**
     * Functions annotated with @Test, collected during semantic analysis.
     */
    public List<Symbol> testcases = List.of();

    // ---- (CGenerator) Monomorphization results (populated by Monomorphization pass) ----

    /**
     * Dependency sub-DAGs bucketed by analysis-order unit (deduplicated):
     * key = type / global variable, value = the mono types it needs.
     */
    @Deprecated
    public Map<Entity, DAGGraph<TypeDefinition>> monoDeps = new LinkedHashMap<>();

    /**
     * 锚点（原始类型 / 全局变量）→ 紧随其后生成的 mono 类型，已按依赖拓扑序排列。
     * 无原始类型锚点的（纯 primitive 依赖，如 [2]int、(int,float)）进 monoHead。
     * <p>mono2 生成顺序重构：取代 {@link #monoDeps} + {@link #monoTrailing} 的
     * 「按消费者分桶 + trailing」两套排序，改为「按依赖锚点后置」单一排序。
     */
    public Map<Entity, List<TypeDefinition>> monoAfter = new LinkedHashMap<>();

    /** 无锚点的 mono 类型（依赖全为 primitive / 无值依赖），按拓扑序。 */
    public List<TypeDefinition> monoHead = new ArrayList<>();

    /**
     * Concretized functions / methods (no generic parameters, prototype and
     * body already resolved). Not part of the dependency graph.
     */
    public List<FunctionDefinition> monoFuncs = new ArrayList<>();

    /**
     * 非泛型类与mono类中有带泛型参数的方法对应的mono方法，后续被转换成MethodFunc放在ClassMeta中
     */
    public Map<Symbol, List<ClassMethod>> monoMethods = new HashMap<>();

    /**
     * 每个需 emit 的类的编译期元数据（方法 → 函数描述符）。由
     * {@link ClassMetadata} pass 在单态化之后填充；key 是 {@code def.symbol()}。
     */
    public SymbolMap<ClassMeta> classMetas = new SymbolMap<>();
    /**
     * 接口、非final类支持运行时多态——即动态派发调用，这是它们的虚表数据，后端可以根据此数据
     * 生成实际的数据结构。
     */
    public SymbolMap<VTable> vtables = new SymbolMap<>();

    /**
     * 强引用类型的 cleanup 函数（FunctionDefinition AST）。由 ReleaserBuilder 填充。
     * key = 需要 cleanup 的强引用 TypeDeclarer（refer = STRONG）。
     */
    public Map<TypeDeclarer, FunctionDefinition> cleanups = new LinkedHashMap<>();

    /**
     * 值类型（refer = none）含强引用内容的 copy 函数（FunctionDefinition AST）。
     * 由 CopyBuilder 填充；key = needsCopy 的值类型 TypeDeclarer。copy 是可选的，
     * 仅含强引用成员/元素的类型才有（Lazy 包装：嵌套类型首次被查时递归生成）。
     */
    public Map<TypeDeclarer, Lazy<FunctionDefinition>> copies = new LinkedHashMap<>();

    /**
     * 未被任何 unit 值依赖引用的具体化类型（仅被函数/方法体或签名引用），
     * 由 mono2 {@code buildUnitDeps} 末尾收集（trailing group），依赖拓扑序。
     * 后端在 {@link #monoDeps} 之后补发这些类型的 typedef/struct。
     */
    @Deprecated
    public DAGGraph<TypeDefinition> monoTrailing = DAGGraph.empty();

    /**
     * 空的语义分析结果表（无任何源符号），用于承载「合成模块」这类无源文件的
     * 虚拟模块。所有集合字段置空，仅 monoHead/monoAfter 等 mono 结果字段保留
     * 默认空容器，供单态化 pass 写入物化类型。
     */
    public static AnalyseSymbolTable empty() {
        var ast = new AnalyseSymbolTable();
        ast.typeList = List.of();
        ast.functionList = List.of();
        ast.constVars = List.of();
        ast.dagVars = DAGGraph.empty();
        ast.enumList = List.of();
        ast.dagPrototypes = DAGGraph.empty();
        ast.dagStructures = DAGGraph.empty();
        ast.dagInterfaces = DAGGraph.empty();
        ast.dagClasses = DAGGraph.empty();
        ast.stringCache = Map.of();
        return ast;
    }

}
