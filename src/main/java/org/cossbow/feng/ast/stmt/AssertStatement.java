package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class AssertStatement extends Statement {
    private Expression condition;

    public AssertStatement(Position pos, Expression condition) {
        super(pos);
        this.condition = condition;
    }

    public Expression condition() {
        return condition;
    }

    public void condition(Expression condition) {
        this.condition = condition;
    }

    @Override
    public AssertStatement mirror() {
        return new AssertStatement(pos(), condition.mirror());
    }
}
