package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.TypeArguments;

public class MemberOfExpression extends PrimaryExpression {
    private PrimaryExpression subject;
    private Identifier member;
    private TypeArguments generic;

    public MemberOfExpression(Position pos,
                              PrimaryExpression subject,
                              Identifier member,
                              TypeArguments generic) {
        super(pos);
        this.subject = subject;
        this.member = member;
        this.generic = generic;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public Identifier member() {
        return member;
    }

    public TypeArguments generic() {
        return generic;
    }

    //


    @Override
    public String toString() {
        if (generic.isEmpty())
            return subject + "." + member;
        return subject + "." + member + generic;
    }
}
