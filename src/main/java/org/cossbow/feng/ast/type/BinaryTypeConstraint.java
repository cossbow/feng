package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.gen.TypeOperator;

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
        return switch (operator) {
            case AND -> left.contains(t) && right.contains(t);
            case OR -> left.contains(t) || right.contains(t);
        };
    }

    @Override
    public TypeView view() {
        return switch (operator) {
            case AND -> left.view().intersect(right.view());
            case OR -> left.view().union(right.view());
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
