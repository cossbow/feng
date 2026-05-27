package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TupleTypeDeclarer;

/**
 * Get the element value of a tuple through the index.
 * The index must be decimal integer literal.
 * <p>
 * For example, the {@code a} is a tuple:
 * <p>
 * Get the element at 0: {@code var v = a.0;}
 * <p>
 * Get the element at 7: {@code var v = a.7;}
 */
public class TupleIndexExpression extends PrimaryExpression {
    /**
     * The type of subject must be {@link TupleTypeDeclarer}
     */
    private final PrimaryExpression subject;
    /**
     * decimal integer literal
     */
    private final int index;

    public TupleIndexExpression(Position pos,
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

    //
    @Override
    public String toString() {
        return subject + "." + index;
    }
}
