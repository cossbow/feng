package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

/**
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

}
