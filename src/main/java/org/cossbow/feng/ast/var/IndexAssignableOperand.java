package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.IndexOfExpression;
import org.cossbow.feng.ast.expr.PrimaryExpression;

public class IndexAssignableOperand extends AssignableOperand {
    private final PrimaryExpression subject;
    private final Expression index;

    public IndexAssignableOperand(Position pos,
                                  PrimaryExpression subject,
                                  Expression index) {
        super(pos);
        this.subject = subject;
        this.index = index;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public Expression index() {
        return index;
    }

    @Override
    public Expression rhs() {
        return new IndexOfExpression(pos(), subject, index);
    }

    //

    @Override
    public String toString() {
        return subject + "[" + index + "]";
    }
}
