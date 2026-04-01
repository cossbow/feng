package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

public class ConditionalExpression extends Expression {
    private Expression condition;
    private Expression yes;
    private Expression not;

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
