package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;

/**
 * Unified class of all binary operation expressions
 * <p>
 * Example:
 * {@code left + right},
 * {@code left - right},
 * {@code left * right},
 * {@code left / right},
 * etc.
 */
public class BinaryExpression extends Expression {
    private final BinaryOperator operator;
    private Expression left, right;

    public BinaryExpression(Position pos,
                            BinaryOperator operator,
                            Expression left, Expression right) {
        super(pos);
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    public BinaryOperator operator() {
        return operator;
    }

    public Expression left() {
        return left;
    }

    public void left(Expression left) {
        this.left = left;
    }

    public Expression right() {
        return right;
    }

    public void right(Expression right) {
        this.right = right;
    }

    //

    @Override
    public String toString() {
        return left + operator.code + right;
    }

    @Override
    public BinaryExpression mirror() {
        return new BinaryExpression(pos(), operator, left.mirror(), right.mirror());
    }

    @Override
    public BinaryExpression mono(GenericMap gm) {
        return monoCopy(new BinaryExpression(pos(), operator,
                left.mono(gm), right.mono(gm)), gm);
    }
}
