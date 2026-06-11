package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

/**
 * Dereference-Operation is used to directly read an instance
 * through a reference.
 *
 *
 * <p>
 * {@code *a}, {@code *a.v}, {@code *a[i]}
 */
public class DereferExpression extends PrimaryExpression {
    /**
     * type of {@code subject} must be reference
     */
    private final PrimaryExpression subject;

    public DereferExpression(Position pos,
                             PrimaryExpression subject) {
        super(pos);
        this.subject = subject;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    //
    @Override
    public String toString() {
        return '*' + subject.toString();
    }
}
