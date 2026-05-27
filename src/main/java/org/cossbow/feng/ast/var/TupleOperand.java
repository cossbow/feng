package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TupleTypeDeclarer;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.PrimaryExpression;
import org.cossbow.feng.ast.expr.TupleIndexExpression;

/**
 * Used to operate element of tuple. Similar to the {@link TupleIndexExpression},
 * use decimal integer literal index to operate it.
 * <p>
 * For example, the {@code a} is a tuple:
 * <p>
 * To update element value at index 2: {@code a.2 = 100;}
 */
public class TupleOperand extends Operand {
    /**
     * The type of subject must be {@link TupleTypeDeclarer}
     */
    private final PrimaryExpression subject;
    /**
     * decimal integer literal
     */
    private final int index;

    public TupleOperand(Position pos,
                        PrimaryExpression subject,
                        int index) {
        super(pos);
        this.subject = subject;
        this.index = index;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public int index() {
        return index;
    }

    @Override
    public Expression rhs() {
        return new TupleIndexExpression(pos(), subject, index);
    }

    //
    @Override
    public String toString() {
        return subject + "." + index;
    }
}
