package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.UnaryOperator;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.PrimitiveTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.util.Optional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * 约束在「运算符」维度上的投影：约束集合中保证可用的运算符下近似。
 * <p>
 * 与 {@link MemberSet}（成员名是开放宇宙）不同，运算符集合是封闭有限的
 * （{@link BinaryOperator} 18 个 / {@link UnaryOperator} 3 个），
 * 可像 {@code domains()} 一样精确计算下近似。
 * <p>
 * 运算符<b>存在性</b>与<b>自定义方法</b>分开保存：
 * <ul>
 *   <li>{@code binary}/{@code unary}：存在性下近似（{@link EnumSet}），
 *       primitive 运算符也在其中（无方法）；</li>
 *   <li>{@code binaryMethods}/{@code unaryMethods}：{@code macro operator}
 *       的自定义方法映射（仅非 null），OR 组合按「结果类型一致」做 sameView 剔除；</li>
 *   <li>index 运算符独立保存 {@code get}/{@code set} 方法。</li>
 * </ul>
 * <p>
 * 组合语义与 {@link MemberSet} 同构：
 * <ul>
 *   <li>AND {@code A & B}：类型须同时满足两侧，运算符取<b>并集</b>；</li>
 *   <li>OR {@code A | B}：满足任一侧，只有两侧都保证的运算符存活，取<b>交集</b>，
 *       自定义方法按结果类型一致（sameView）保留，否则剔除（发射歧义）。</li>
 * </ul>
 */
public class OperatorSet {
    private final EnumSet<BinaryOperator> binary;
    private final EnumSet<UnaryOperator> unary;
    private final EnumMap<BinaryOperator, ClassMethod> binaryMethods;
    private final EnumMap<UnaryOperator, ClassMethod> unaryMethods;
    private final Optional<ClassMethod> indexGet;
    private final Optional<ClassMethod> indexSet;

    private OperatorSet(EnumSet<BinaryOperator> binary,
                        EnumSet<UnaryOperator> unary,
                        EnumMap<BinaryOperator, ClassMethod> binaryMethods,
                        EnumMap<UnaryOperator, ClassMethod> unaryMethods,
                        Optional<ClassMethod> indexGet,
                        Optional<ClassMethod> indexSet) {
        this.binary = binary;
        this.unary = unary;
        this.binaryMethods = binaryMethods;
        this.unaryMethods = unaryMethods;
        this.indexGet = indexGet;
        this.indexSet = indexSet;
    }

    private static final OperatorSet EMPTY =
            new OperatorSet(EnumSet.noneOf(BinaryOperator.class),
                    EnumSet.noneOf(UnaryOperator.class),
                    new EnumMap<>(BinaryOperator.class),
                    new EnumMap<>(UnaryOperator.class),
                    Optional.empty(), Optional.empty());

    /**
     * 空下近似：无保证可用运算符。
     */
    public static OperatorSet empty() {
        return EMPTY;
    }

    /**
     * 该约束保证可用的二元运算符。
     */
    public EnumSet<BinaryOperator> binary() {
        return binary;
    }

    /**
     * 该约束保证可用的一元运算符。
     */
    public EnumSet<UnaryOperator> unary() {
        return unary;
    }

    public boolean hasBinary(BinaryOperator op) {
        return binary.contains(op);
    }

    public boolean hasUnary(UnaryOperator op) {
        return unary.contains(op);
    }

    public Optional<ClassMethod> indexGet() {
        return indexGet;
    }

    public Optional<ClassMethod> indexSet() {
        return indexSet;
    }

    /**
     * 该二元运算符的自定义方法（{@code macro operator}）；primitive 或未实现 → empty。
     */
    public Optional<ClassMethod> binaryMethod(BinaryOperator op) {
        return Optional.of(binaryMethods.get(op));
    }

    /**
     * 该一元运算符的自定义方法；primitive 或未实现 → empty。
     */
    public Optional<ClassMethod> unaryMethod(UnaryOperator op) {
        return Optional.of(unaryMethods.get(op));
    }

    // ──────────────── 提取 ────────────────

    /**
     * 从具体类型提取运算符下近似。
     * <p>
     * 基本类型（primitive）：按 {@link PrimitiveTypeDeclarer} 的 kind 推导
     * （与 {@code primitiveBinOp} / {@code checkUnaryOperate} 一致）；
     * class：取其 {@code binaryOperators}/{@code unaryOperators}/{@code indexOperator} 表；
     * 其余类型无保证运算符 → 空。
     */
    public static OperatorSet of(TypeDeclarer t) {
        if (t instanceof PrimitiveTypeDeclarer ptd) {
            var b = EnumSet.noneOf(BinaryOperator.class);
            var u = EnumSet.noneOf(UnaryOperator.class);
            var p = ptd.primitive();
            if (p.isInteger()) {
                b.addAll(BinaryOperator.SetMath);
                b.addAll(BinaryOperator.SetBits);
                b.addAll(BinaryOperator.SetRel);
                u.add(UnaryOperator.POSITIVE);
                u.add(UnaryOperator.NEGATIVE);
                u.add(UnaryOperator.INVERT);
            } else if (p.isFloat()) {
                b.addAll(BinaryOperator.SetMath);
                b.addAll(BinaryOperator.SetRel);
                u.add(UnaryOperator.POSITIVE);
                u.add(UnaryOperator.NEGATIVE);
            } else if (p.isBool()) {
                b.addAll(BinaryOperator.SetBool);
                u.add(UnaryOperator.INVERT);
            }
            return new OperatorSet(b, u,
                    new EnumMap<>(BinaryOperator.class),
                    new EnumMap<>(UnaryOperator.class),
                    Optional.empty(), Optional.empty());
        }

        if (t instanceof DerivedTypeDeclarer dtd
                && dtd.def() instanceof ClassDefinition cd) {
            var b = EnumSet.noneOf(BinaryOperator.class);
            b.addAll(cd.binaryOperators().keySet());
            var u = EnumSet.noneOf(UnaryOperator.class);
            u.addAll(cd.unaryOperators().keySet());
            var bm = new EnumMap<BinaryOperator, ClassMethod>(BinaryOperator.class);
            bm.putAll(cd.binaryOperators());
            var um = new EnumMap<UnaryOperator, ClassMethod>(UnaryOperator.class);
            um.putAll(cd.unaryOperators());
            var get = Optional.<ClassMethod>empty();
            var set = Optional.<ClassMethod>empty();
            if (cd.indexOperator().has()) {
                var io = cd.indexOperator().must();
                get = io.get();
                set = io.set();
            }
            return new OperatorSet(b, u, bm, um, get, set);
        }

        return empty();
    }

    // ──────────────── 组合 ────────────────

    /**
     * AND 约束 {@code A & B}：类型同时满足两侧，运算符取并集。
     * index 方法两侧都有则任取其一（并集语义下两侧都必须实现）。
     */
    public static OperatorSet and(OperatorSet a, OperatorSet b) {
        var bset = EnumSet.copyOf(a.binary);
        bset.addAll(b.binary);
        var uset = EnumSet.copyOf(a.unary);
        uset.addAll(b.unary);
        var bm = new EnumMap<BinaryOperator, ClassMethod>(a.binaryMethods);
        b.binaryMethods.forEach(bm::putIfAbsent);
        var um = new EnumMap<UnaryOperator, ClassMethod>(a.unaryMethods);
        b.unaryMethods.forEach(um::putIfAbsent);
        var get = a.indexGet.has() ? a.indexGet : b.indexGet;
        var set = a.indexSet.has() ? a.indexSet : b.indexSet;
        return new OperatorSet(bset, uset, bm, um, get, set);
    }

    /**
     * OR 约束 {@code A | B}：满足任一侧，只有两侧都保证的运算符存活（交集）。
     * <p>
     * 存在性交集保留的前提下，还要求<b>发射形式一致</b>：
     * 模板体只分析一次，{@code a op b} 的发射形式必须在分析期统一决定——
     * 全 primitive（两侧都无方法）或两侧同为自定义方法（结果类型一致），
     * 否则一侧 primitive 一侧自定义（或两侧方法结果类型不同）无法统一发射，
     * 该运算符从投影剔除（模板体拒绝）。
     */
    public static OperatorSet or(OperatorSet a, OperatorSet b) {
        var bset = EnumSet.copyOf(a.binary);
        bset.retainAll(b.binary);
        var uset = EnumSet.copyOf(a.unary);
        uset.retainAll(b.unary);
        var bm = new EnumMap<BinaryOperator, ClassMethod>(BinaryOperator.class);
        mergeBinary(bset, bm, a, b);
        var um = new EnumMap<UnaryOperator, ClassMethod>(UnaryOperator.class);
        mergeUnary(uset, um, a, b);
        var get = sameIndex(a.indexGet, b.indexGet)
                ? a.indexGet : Optional.<ClassMethod>empty();
        var set = sameIndex(a.indexSet, b.indexSet)
                ? a.indexSet : Optional.<ClassMethod>empty();
        return new OperatorSet(bset, uset, bm, um, get, set);
    }

    /**
     * 合并二元运算符的发射形式（存在性集合 {@code bset} 为已算好的交集）。
     * <p>
     * 两侧都无方法 → primitive 发射；两侧都有方法且<b>结果类型一致</b>（sameView，
     * 与 {@link MemberSet} 同判据——关系运算符两侧都 {@code bool}，算术运算符
     * 两侧须返回同一类型）→ 方法发射，任取一侧方法（单态化后按实参类名重解析）；
     * 否则发射歧义 → 剔除。
     * <p>
     * 不能按 prototype 全等判定：两侧运算符参数是各自类的 {@code &self} 虚引用
     * （{@code A.lt} 参数 {@code &A}、{@code B.lt} 参数 {@code &B}），全等比较
     * 会把 {@code A | B} 这类合法组合误剔除。
     */
    private static void mergeBinary(EnumSet<BinaryOperator> bset,
                                    EnumMap<BinaryOperator, ClassMethod> bm,
                                    OperatorSet a, OperatorSet b) {
        for (var op : EnumSet.copyOf(bset)) {
            var am = a.binaryMethods.get(op);
            var b0 = b.binaryMethods.get(op);
            if (am == null && b0 == null) continue;      // 两侧 primitive
            if (am != null && b0 != null
                    && am.prototype().returnSet().equals(b0.prototype().returnSet())) {
                bm.put(op, am);                          // 结果类型一致 → 方法发射
                continue;
            }
            bset.remove(op);                             // 发射歧义 → 剔除
        }
    }

    private static void mergeUnary(EnumSet<UnaryOperator> uset,
                                   EnumMap<UnaryOperator, ClassMethod> um,
                                   OperatorSet a, OperatorSet b) {
        for (var op : EnumSet.copyOf(uset)) {
            var am = a.unaryMethods.get(op);
            var b0 = b.unaryMethods.get(op);
            if (am == null && b0 == null) continue;
            if (am != null && b0 != null
                    && am.prototype().returnSet().equals(b0.prototype().returnSet())) {
                um.put(op, am);
                continue;
            }
            uset.remove(op);
        }
    }

    private static boolean sameIndex(Optional<ClassMethod> a,
                                     Optional<ClassMethod> b) {
        if (a.none() || b.none()) return false;
        return a.get().prototype().equals(b.get().prototype());
    }

    //
    @Override
    public String toString() {
        return "OperatorSet[" + binary.size() + " binary, "
                + unary.size() + " unary]"
                + (indexGet.has() || indexSet.has() ? " +index" : "");
    }
}
