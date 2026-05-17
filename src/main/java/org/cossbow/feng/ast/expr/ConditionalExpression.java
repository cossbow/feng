package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

/**
 * Unlike if..else statements, conditional expressions can
 * have a direct return value.
 * <p>
 * Example: {@code a ? b : c}
 * <p>
 * Based on the calculated value of condition {@code a},
 * if it is {@code true}, then the value of {@code b} will
 * be used; if it is {@code false}, then the value of
 * {@code c} will be used.
 *
 */
public class ConditionalExpression extends Expression {
    /**
     * Must be bool type
     */
    private final Expression condition;
    private final Expression yes;
    private final Expression not;

    public ConditionalExpression(Position pos,
                                 Expression condition,
                                 Expression yes,
                                 Expression not) {
        super(pos);
        this.condition = condition;
        this.yes = yes;
        this.not = not;
    }

    public Expression condition() {
        return condition;
    }

    public Expression yes() {
        return yes;
    }

    public Expression not() {
        return not;
    }

    //
    public String toString() {
        return condition + "?" + yes + ":" + not;
    }
}
