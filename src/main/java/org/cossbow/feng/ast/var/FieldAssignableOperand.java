package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.MemberOfExpression;
import org.cossbow.feng.ast.expr.PrimaryExpression;
import org.cossbow.feng.ast.gen.TypeArguments;

public class FieldAssignableOperand extends AssignableOperand {
    private final PrimaryExpression subject;
    private final Identifier field;

    public FieldAssignableOperand(Position pos,
                                  PrimaryExpression subject,
                                  Identifier field) {
        super(pos);
        this.subject = subject;
        this.field = field;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public Identifier field() {
        return field;
    }

    @Override
    public Expression rhs() {
        return new MemberOfExpression(pos(), subject, field, TypeArguments.EMPTY);
    }

    //


    @Override
    public String toString() {
        return subject + "." + field;
    }
}
