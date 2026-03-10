package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

public class CheckNilExpression extends PrimaryExpression {
    private final Expression subject;
    private final boolean nil;

    public CheckNilExpression(Position pos,
                              Expression subject,
                              boolean nil) {
        super(pos);
        this.subject = subject;
        this.nil = nil;
    }

    public Expression subject() {
        return subject;
    }

    public boolean nil() {
        return nil;
    }

}
