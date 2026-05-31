package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.DereferExpression;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.PrimaryExpression;

/**
 * Dereference operand: used for directly modify the instance.
 * <p>
 * Example:
 * <p>
 * {@code *a = t;}
 * <p>
 * {@code *a.b = x;}
 * <p>
 * {@code *a.[0] = y;}
 * <p>
 * {@code *a.0 = z;}
 */
public class DereferOperand extends Operand {
    /**
     * The target of dereference must be a reference type
     */
    private final PrimaryExpression subject;

    public DereferOperand(Position pos,
                          PrimaryExpression subject) {
        super(pos);
        this.subject = subject;
    }

    public PrimaryExpression subject() {
        return subject;
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
