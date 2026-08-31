package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.gen.TypeOperator;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Binary constraint {@code A & B} or {@code A | B}.
 * <p>
 * {@code &} is intersection (AND): contains types in <em>both</em> operands.
 * {@code |} is union (OR): contains types in <em>either</em> operand.
 */
public class BinaryTypeConstraint extends TypeConstraint {
    private final TypeOperator operator;
    private final TypeConstraint left, right;

    public BinaryTypeConstraint(Position pos,
                                TypeOperator operator,
                                TypeConstraint left,
                                TypeConstraint right) {
        super(pos);
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    public TypeOperator operator() {
        return operator;
    }

    public TypeConstraint left() {
        return left;
    }

    public TypeConstraint right() {
        return right;
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        if (t instanceof GenericTypeDeclarer gtd) {
            // 类型变量分支（§3.4）：委托 include 做集合包含
            return containsByConstraint(gtd);
        }
        return switch (operator) {
            case AND -> left.contains(t) && right.contains(t);
            case OR -> left.contains(t) || right.contains(t);
        };
    }

    @Override
    public boolean include(TypeConstraint tc) {
        var x = normalize(tc);
        if (equals(x)) return true;
        return switch (operator) {
            // 子集 ⊆ 交 ⟺ 分别 ⊆ 两侧
            case AND -> left.include(x) && right.include(x);
            // 子集 ⊆ 并：⊆ 任一侧即可（不完整但不失 sound）
            case OR -> left.include(x) || right.include(x);
        };
    }

    @Override
    public Tri referenced() {
        return switch (operator) {
            case AND -> Tri.meet(left.referenced(), right.referenced());
            case OR -> Tri.join(left.referenced(), right.referenced());
        };
    }

    @Override
    public MemberSet members() {
        return switch (operator) {
            case AND -> MemberSet.and(left.members(), right.members());
            case OR -> MemberSet.or(left.members(), right.members());
        };
    }

    @Override
    public Tri newable() {
        return switch (operator) {
            case AND -> Tri.meet(left.newable(), right.newable());
            case OR -> Tri.join(left.newable(), right.newable());
        };
    }

    @Override
    public EnumSet<TypeDomain> domains() {
        return switch (operator) {
            case AND -> {
                var r = EnumSet.copyOf(left.domains());
                r.retainAll(right.domains());
                yield r;
            }
            case OR -> {
                var r = EnumSet.copyOf(left.domains());
                r.addAll(right.domains());
                yield r;
            }
        };
    }

    //

    @Override
    public boolean equals(Object o) {
        return o instanceof BinaryTypeConstraint c
                && operator == c.operator &&
                left.equals(c.left) &&
                right.equals(c.right);
    }

    @Override
    public int hashCode() {
        int result = operator.hashCode();
        result = 31 * result + left.hashCode();
        result = 31 * result + right.hashCode();
        return result;
    }

    //
    @Override
    public String toString() {
        return "(" + left + " " + operator.symbol + " " + right + ")";
    }
}
