package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

public class DereferExpression extends PrimaryExpression {
    private PrimaryExpression subject;

    public DereferExpression(Position pos,
                             PrimaryExpression subject) {
        super(pos);
        this.subject = subject;
    }

    public PrimaryExpression subject() {
        return subject;
    }

}
