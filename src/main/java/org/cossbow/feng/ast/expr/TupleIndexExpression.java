package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TupleTypeDeclarer;
import org.cossbow.feng.ast.gen.GenericMap;

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

    @Override
    public boolean unbound() {
        return subject.unbound();
    }

    @Override
    public TupleIndexExpression mirror() {
        return new TupleIndexExpression(pos(), subject.mirror(), index);
    }

    @Override
    public TupleIndexExpression mono(GenericMap gm) {
        return monoCopy(new TupleIndexExpression(pos(), subject.mono(gm), index), gm);
    }

    //
    @Override
    public String toString() {
        return subject + "." + index;
    }
}
