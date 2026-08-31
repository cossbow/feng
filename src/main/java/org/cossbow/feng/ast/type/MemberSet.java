package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Optional;

import java.util.LinkedHashSet;

/**
 * 成员集下近似：约束在「字段 / 方法」两个无界维度上的公共成员投影。
 * <p>
 * 与有限维度（引用性等）不同，成员名是开放宇宙，只能求「保证存在」的下近似，
 * 补集下坍缩为空。
 * <p>
 * 替代被删除的 {@code TypeView}。
 */
public class MemberSet {

    private final ReadMap<Identifier, ? extends Field> fields;
    private final ReadMap<Identifier, ? extends Method> methods;
    private final boolean hasRequiredInit;

    private MemberSet(ReadMap<Identifier, ? extends Field> fields,
                      ReadMap<Identifier, ? extends Method> methods,
                      boolean hasRequiredInit) {
        this.fields = fields;
        this.methods = methods;
        this.hasRequiredInit = hasRequiredInit;
    }

    private static final MemberSet EMPTY =
            new MemberSet(new IdentifierMap<>(),
                    new IdentifierMap<>(), false);

    /**
     * 空下近似：无公共字段、无公共方法、无必填初始化。
     */
    public static MemberSet empty() {
        return EMPTY;
    }

    public Optional<? extends Field> field(Identifier name) {
        return fields.tryGet(name);
    }

    public Optional<? extends Method> method(Identifier name) {
        return methods.tryGet(name);
    }

    public ReadMap<Identifier, ? extends Field> fields() {
        return fields;
    }

    public ReadMap<Identifier, ? extends Method> methods() {
        return methods;
    }

    /**
     * 某个成员携带必填初始化（{@code f.type().requiredInit()}）。
     * AND / OR 组合时该判据以 OR 存活——字段信息可能被组合丢弃，
     * 但「必填」这一事实必须保留。
     */
    public boolean hasRequiredInit() {
        return hasRequiredInit;
    }

    // ──────────────── 提取 ────────────────

    /**
     * 从具体类型提取完整成员下近似（字段 / 方法 / 必填判据）。
     * 字段与方法的类型经声明类型的泛型映射实例化。
     */
    public static MemberSet of(TypeDeclarer t) {
        var f = TypeTool.fieldsOf(t, t);
        var m = TypeTool.methodsOf(t, t);
        return new MemberSet(f, m, TypeTool.hasRequiredInitOf(f));
    }


    // ──────────────── 组合 ────────────────

    /**
     * AND 约束 {@code A & B}：类型须同时满足两侧，成员取并集，
     * 同名不同形态（var/const、@X、escaped、modifier 等）剔除。
     */
    public static MemberSet and(MemberSet a, MemberSet b) {
        return new MemberSet(unionFields(a.fields, b.fields),
                unionMethods(a.methods, b.methods),
                a.hasRequiredInit || b.hasRequiredInit);
    }

    /**
     * OR 约束 {@code A | B}：类型满足任一侧，只有两侧都保证的成员才存活，
     * 取交集，冲突剔除。
     */
    public static MemberSet or(MemberSet a, MemberSet b) {
        return new MemberSet(intersectFields(a.fields, b.fields),
                intersectMethods(a.methods, b.methods),
                a.hasRequiredInit || b.hasRequiredInit);
    }

    // ──────────────── 组合辅助 ────────────────

    private static boolean sameView(Field fa, Field fb) {
        return fa.type().equals(fb.type()) &&
                fa.type().sync() == fb.type().sync() &&
                fa.immutable() == fb.immutable() &&
                fa.modifier().equals(fb.modifier());
    }

    private static boolean sameView(Method fa, Method fb) {
        return fa.prototype().equals(fb.prototype()) &&
                fa.escaped() == fb.escaped() &&
                fa.unmodifiable() == fb.unmodifiable() &&
                fa.modifier().equals(fb.modifier());
    }

    private static ReadMap<Identifier, ? extends Field>
    intersectFields(ReadMap<Identifier, ? extends Field> a,
                    ReadMap<Identifier, ? extends Field> b) {
        // OR：成员仅当两侧都有且 sameView 才保证；空侧贡献无。
        if (a.isEmpty() || b.isEmpty()) return new IdentifierMap<>();
        var r = new IdentifierMap<Field>();
        for (var f : a) {
            var o = b.tryGet(f.name());
            if (o.has() && sameView(o.get(), f)) r.add(f.name(), f);
        }
        return r;
    }

    private static ReadMap<Identifier, ? extends Field>
    unionFields(ReadMap<Identifier, ? extends Field> a,
                ReadMap<Identifier, ? extends Field> b) {
        // AND：成员取并集；同名不同形态无保证，剔除。
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        var r = new IdentifierMap<Field>();
        var names = new LinkedHashSet<Identifier>();
        for (var f : a) names.add(f.name());
        for (var f : b) names.add(f.name());
        for (var name : names) {
            var fa = a.tryGet(name);
            var fb = b.tryGet(name);
            if (fa.none()) {
                r.add(name, fb.get());
            } else if (fb.none()) {
                r.add(name, fa.get());
            } else if (sameView(fa.get(), fb.get())) {
                r.add(name, fa.get());
            }
        }
        return r;
    }

    private static ReadMap<Identifier, ? extends Method>
    intersectMethods(ReadMap<Identifier, ? extends Method> a,
                     ReadMap<Identifier, ? extends Method> b) {
        if (a.isEmpty() || b.isEmpty())
            return new IdentifierMap<>();
        var r = new IdentifierMap<Method>();
        for (var m : a) {
            var o = b.tryGet(m.name());
            if (o.has() && sameView(o.get(), m)) r.add(m.name(), m);
        }
        return r;
    }

    private static ReadMap<Identifier, ? extends Method>
    unionMethods(ReadMap<Identifier, ? extends Method> a,
                 ReadMap<Identifier, ? extends Method> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        var r = new IdentifierMap<Method>();
        var names = new LinkedHashSet<Identifier>();
        for (var m : a) names.add(m.name());
        for (var m : b) names.add(m.name());
        for (var name : names) {
            var ma = a.tryGet(name);
            var mb = b.tryGet(name);
            if (ma.none()) {
                r.add(name, mb.get());
            } else if (mb.none()) {
                r.add(name, ma.get());
            } else if (sameView(ma.get(), mb.get())) {
                r.add(name, ma.get());
            }
        }
        return r;
    }


    //
    @Override
    public String toString() {
        return "MemberSet[" + fields.size() + " fields, "
                + methods.size() + " methods]";
    }
}