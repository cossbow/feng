package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

public class IndexOfExpression extends PrimaryExpression {
    private final PrimaryExpression subject;
    private final Expression index;

    public IndexOfExpression(Position pos,
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
}
