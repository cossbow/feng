package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;

/**
 * Expressions used to determine nil are generated
 * during analysis for ease of analysis
 */
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

    @Override
    public CheckNilExpression mirror() {
        return new CheckNilExpression(pos(), subject.mirror(), nil);
    }

    @Override
    public CheckNilExpression mono(GenericMap gm) {
        return monoCopy(new CheckNilExpression(pos(), subject.mono(gm), nil), gm);
    }

    //
    @Override
    public String toString() {
        return subject + (nil ? " == nil" : " != nil");
    }
}
