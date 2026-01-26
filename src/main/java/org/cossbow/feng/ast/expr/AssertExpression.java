package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;

public class AssertExpression extends PrimaryExpression {
    private PrimaryExpression subject;
    private DerivedTypeDeclarer type;

    public AssertExpression(Position pos,
                            PrimaryExpression subject,
                            DerivedTypeDeclarer type) {
        super(pos);
        this.subject = subject;
        this.type = type;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public DerivedTypeDeclarer type() {
        return type;
    }

    @Override
    public boolean unbound() {
        return subject.unbound();
    }

    private volatile boolean needCheck;

    public boolean needCheck() {
        return needCheck;
    }

    public void needCheck(boolean needCheck) {
        this.needCheck = needCheck;
    }
}
