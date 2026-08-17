package org.cossbow.feng.analysis.meta;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Mangle;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.oop.ClassDefinition;

/**
 * 编译期类元数据：把「当前类的可派发方法」降级为完整函数描述符 {@link MethodFunc}。
 *
 * <p>职责单一：仅覆盖 {@code def.methods()}（含 resourceFree）与运算符 /
 * 索引宏方法，以及 mono2 产物里挂在 {@code master} 上的方法级泛型实例化；
 * 不含继承（{@code allMethods()}/{@code inheritMethods()}）、不含接口派发。
 *
 * <p>只读不改 AST，不 emit，不产出 C 结构。{@code isFinal} 只是交给后端
 * 选择 static / dynamic 派发的标志。
 */
public final class ClassMeta {

    private final ClassDefinition def;
    private final boolean isFinal;
    /**
     * 方法名 → 函数描述符。仅覆盖当前类定义的方法（含运算符 / 索引 / resourceFree
     * 及方法级泛型实例化）。按定义序插入，保证后端按序 emit 且行为稳定。
     */
    private final IdentifierMap<MethodFunc> methods = new IdentifierMap<>();

    public ClassMeta(ClassDefinition def) {
        this.def = def;
        this.isFinal = def.isFinal();
    }

    public ClassDefinition def() {
        return def;
    }

    public boolean isFinal() {
        return isFinal;
    }

    /**
     * 方法名 → 函数描述符。方法名在单个类内唯一，{@link Identifier#equals} 按 value
     * 相等，可作 key。
     */
    public IdentifierMap<MethodFunc> methods() {
        return methods;
    }

    public void addMethod(MethodFunc mf) {
        methods.add(mf.name(), mf);
    }

    /**
     * 该类的析构函数（Feng$destroy_X）。由 ReleaserBuilder 填充；
     * 非 final 类会被 VTable.destroy 引用。
     */
    private MethodFunc destroy;

    public MethodFunc destroy() {
        return destroy;
    }

    public void destroy(MethodFunc destroy) {
        this.destroy = destroy;
    }

    // ---- 函数符号公式（唯一权威，与 CGenerator 输出一致） ----

    /**
     * 类的 C 符号名：{@code [module$]Name}。module 存在时用完整路径
     * （{@code ModulePath.toString()} = {@code join("$")}），不存在时仅类名。
     *
     * <p>等价于 CGenerator {@code write(Symbol)} = {@code module.toString() +
     * write(name)} 的字符串化（{@code write(Identifier)} 输出 {@code $value}）。
     */
    public static String symbolName(Symbol s) {
        return Mangle.symbolName(s);
    }

    /**
     * 方法的 C 函数符号：{@code symbolName(def.symbol()) + "$" + methodName.value()}。
     * 对运算符 / 索引 / resourceFree，{@code methodName.value()} 已是 macro id
     * （{@code feng$macro$operator$add} 等）；对方法级泛型实例化，是 mangle 名
     * （{@code map_Bool} 等）。三种情形公式统一。
     */
    public static String methodSymbol(ClassDefinition def, Identifier methodName) {
        return symbolName(def.symbol()) + "$" + methodName.value();
    }

    @Override
    public String toString() {
        return "ClassMeta" + def;
    }
}