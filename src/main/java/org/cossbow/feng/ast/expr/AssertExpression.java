package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

public class AssertExpression extends PrimaryExpression {
    private PrimaryExpression subject;
    private TypeDeclarer type;

    public AssertExpression(Position pos,
                            PrimaryExpression subject,
                            TypeDeclarer type) {
        super(pos);
        this.subject = subject;
        this.type = type;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public TypeDeclarer type() {
        return type;
    }
}
