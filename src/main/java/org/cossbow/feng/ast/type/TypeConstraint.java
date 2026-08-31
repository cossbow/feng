package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.EnumSet;

/**
 * Generic type parameter constraint, expressed as a {@link TypeConstraint}.
 * <p>
 * A constraint is a set of types: {@link #contains(TypeDeclarer)} answers
 * whether a given type satisfies the constraint.
 * <p>
 * {@code class Stack<T: *any>} — the constraint is {@code *any}
 * (all reference types).
 */
abstract
public class TypeConstraint extends Entity {
    public TypeConstraint(Position pos) {
        super(pos);
    }

    /**
     * Whether the given type satisfies this constraint.
     */
    abstract public boolean contains(TypeDeclarer t);

    /**
     * 集合包含判定：{@code tc} 的集合 ⊆ 本约束的集合。
     * <p>
     * {@code this.include(tc)} ⟺ ∀t ∈ Types: {@code tc.contains(t) ⟹ this.contains(t)}。
     * sound（不允许误放行），允许不完整（false 阴性）。
     */
    abstract public boolean include(TypeConstraint tc);

    /**
     * 剥壳：循环展开 {@link ParenTypeConstraint} 与 {@link ConceptTypeConstraint}，
     * 返回语法等价的核心约束。概念表达式是 DAG（分析期已拒自引用），展开终止。
     */
    protected static TypeConstraint normalize(TypeConstraint c) {
        var x = c;
        while (true) {
            if (x instanceof ParenTypeConstraint pc) {
                x = pc.child();
                continue;
            }
            if (x instanceof ConceptTypeConstraint cc) {
                x = cc.concept().expr();
                continue;
            }
            return x;
        }
    }

    /**
     * 类型变量分支：实参是 {@link GenericTypeDeclarer} 时，以其自身约束集合
     * 做包含判定 {@code include(oc)}。无约束的类型变量 = 全集，
     * 不满足任何非全集约束 → false。
     */
    protected boolean containsByConstraint(GenericTypeDeclarer t) {
        var oc = t.param().constraint();
        return !oc.none() && include(oc.get());
    }

    /**
     * 该约束在「引用性」维度上的投影。
     * <p>
     * 以「是引用类型」为属性：{@link Tri#YES} = 必为引用，
     * {@link Tri#NO} = 必为值，{@link Tri#BOTH} = 可值可引用。
     * 与 {@link #contains} 一样是判定式，但只提取引用性这一个维度。
     */
    abstract public Tri referenced();

    /**
     * 该约束在「成员集」维度上的下近似（字段 / 方法 / 必填初始化）。
     * 空（{@link MemberSet#empty()}）表示无公共成员下近似。
     */
    abstract public MemberSet members();

    /**
     * 该约束在「能否 new 实例」维度上的投影。
     * {@link Tri#YES} = 必可 new，{@link Tri#NO} = 必不可 new，{@link Tri#BOTH} = 不确定。
     */
    abstract public Tri newable();

    /**
     * 该约束在「域」维度上的投影：可能的 {@link TypeDomain} 集合。
     * 域是有限 8 值集合，补集在此维度上精确闭合。
     */
    abstract public EnumSet<TypeDomain> domains();

    /**
     * 全部 8 个域（叶子约束对域无限制时的投影）。
     */
    protected static EnumSet<TypeDomain> allDomains() {
        return EnumSet.allOf(TypeDomain.class);
    }

    //

    abstract public boolean equals(Object obj);

    abstract public int hashCode();

}
