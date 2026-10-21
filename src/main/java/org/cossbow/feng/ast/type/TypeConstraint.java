package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
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
     * 该约束在「可选性」维度上的投影。
     * <p>
     * 以「可选性」为属性：{@link Tri#YES} = 必为optional，
     * {@link Tri#NO} = 必为required，{@link Tri#BOTH} = 两种都可能。
     * 与 {@link #contains} 一样是判定式，但只提取可选性这一个维度。
     */
    abstract public Tri optional();

    /**
     * 该约束在「不可修改」维度上的投影。
     * <p>
     * 以「不可修改性」为属性：{@link Tri#YES} = 必为不可修改，
     * {@link Tri#NO} = 必为可修改，{@link Tri#BOTH} = 两种都可能。
     * 与 {@link #contains} 一样是判定式，但只提取不可修改性这一个维度。
     */
    abstract public Tri unmodifiable();

    /**
     * 该约束在「成员集」维度上的下近似（字段 / 方法 / 必填初始化）。
     * 空（{@link MemberSet#empty()}）表示无公共成员下近似。
     */
    abstract public MemberSet members();

    /**
     * 该约束在「newable」维度上的投影。
     * {@link Tri#YES} = 必可 new，
     * {@link Tri#NO} = 必不可 new，
     * {@link Tri#BOTH} = 不确定。
     */
    abstract public Tri newable();

    /**
     * 该约束在「TypeDomain」维度上的投影：可能的 {@link TypeDomain} 集合。
     * 域是有限 8 值集合，补集在此维度上精确闭合。
     */
    abstract public EnumSet<TypeDomain> domains();

    /**
     * 全部 8 个域（叶子约束对域无限制时的投影）。
     */
    protected static EnumSet<TypeDomain> allDomains() {
        return EnumSet.allOf(TypeDomain.class);
    }

    /**
     * 该约束在「标记有attribute」维度上的投影
     */
    abstract public Tri hasAttr(Symbol attr);


    /**
     * 该约束在「是否包含特定concept」维度上的投影
     */
    abstract public Tri has(Concept c);

    /**
     * 该约束在「运算符」维度上的投影：约束集合中保证可用的运算符下近似。
     * <p>
     * 运算符集合是封闭有限的，与 {@code domains()} 同属可精确计算的有限维度
     * （区别：primitive 运算符是类型代数属性，自定义运算符来自 class 的
     * {@code binaryOperators}/{@code unaryOperators}/{@code indexOperator} 表）。
     * 空（{@link OperatorSet#empty()}）表示无保证可用运算符。
     */
    abstract public OperatorSet operators();

    //

    abstract public boolean equals(Object obj);

    abstract public int hashCode();

}
