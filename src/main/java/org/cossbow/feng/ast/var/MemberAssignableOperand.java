package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.PrimaryExpression;

public class MemberAssignableOperand extends AssignableOperand {
    private PrimaryExpression subject;
    private Identifier member;

    public MemberAssignableOperand(Position pos,
                                   PrimaryExpression subject,
                                   Identifier member) {
        super(pos);
        this.subject = subject;
        this.member = member;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public Identifier member() {
        return member;
    }
}
