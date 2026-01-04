package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.DereferExpression;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.PrimaryExpression;

public class DereferOperand extends Operand {
    private PrimaryExpression subject;

    public DereferOperand(Position pos,
                          PrimaryExpression subject) {
        super(pos);
        this.subject = subject;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public void subject(PrimaryExpression subject) {
        this.subject = subject;
    }

    @Override
    public Expression rhs() {
        return new DereferExpression(pos(), subject);
    }

    //

    @Override
    public String toString() {
        return '*' + subject.toString();
    }
}
