package org.cossbow.feng.ast.type;

/**
 * 三态投影值，用于表达约束在某个二值特征维度上的「像」。
 * <p>
 * 约束是类型集合，对任一二值特征维度（如「引用性」= {引用, 值}），
 * 约束在该维度上的投影是幂集
 * {@code 2^{a,b} = {∅, {a}, {b}, {a,b}}} 的子集。排除 {@code ∅}（无效约束，
 * 在约束检查阶段排查）后，剩余三种情况即本枚举的三个取值：
 * <ul>
 *   <li>{@link #YES} —— 投影为单元素 {@code {a}}，属性确定成立；</li>
 *   <li>{@link #NO}  —— 投影为单元素 {@code {b}}，属性确定不成立（= 另一 atom）；</li>
 *   <li>{@link #BOTH} —— 投影为全集 {@code {a,b}}，两者皆可（确定的正信息，非「未知」）。</li>
 * </ul>
 * <p>
 * 每个维度独立约定其「属性」方向。例如 {@code referenced()} 以「是引用类型」为属性：
 * {@code YES=必引用}，{@code NO=必值}，{@code BOTH=可值可引用}。
 */
public enum Tri {
    YES,
    NO,
    BOTH;

    /**
     * AND 组合（约束交集 {@code A & B} 在单一维度上的投影交）。
     */
    public static Tri meet(Tri a, Tri b) {
        if (a == b) return a;
        if (a == BOTH) return b;
        if (b == BOTH) return a;
        // YES ⊓ NO 无公共元素，理论上是 ∅（无效约束），
        // 交由约束检查阶段排查；此处在投影层保守归为 BOTH，
        // 使 "== NO" 类判据保持「拒绝」方向，不误放行。
        return BOTH;
    }

    /**
     * OR 组合（约束并集 {@code A | B} 在单一维度上的投影并）。
     */
    public static Tri join(Tri a, Tri b) {
        if (a == b) return a;
        if (a == BOTH || b == BOTH) return BOTH;
        // YES | NO 二者皆可能出现 → BOTH
        return BOTH;
    }

    /**
     * 补集 {@code !C} 在单一维度上的投影取反。
     * <p>
     * 注意：仅在约束是「纯引用性/纯值性谓词」时精确；对跨维度谓词（如 class），
     * 补集投影不等于投影取反。故 {@code BOTH} 的补集本为 {@code ∅}，
     * 此处保守返回 {@code BOTH}，由检查阶段对「无效约束」单独排查。
     */
    public static Tri not(Tri a) {
        return switch (a) {
            case YES -> NO;
            case NO -> YES;
            case BOTH -> BOTH;
        };
    }
}