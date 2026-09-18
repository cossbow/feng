package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;

public class SliceOfExpression extends Expression {
    private final PrimaryExpression subject;
    private final Expression start;
    private final Expression end;

    public SliceOfExpression(Position pos,
                             PrimaryExpression subject,
                             Expression start,
                             Expression end) {
        super(pos);
        this.subject = subject;
        this.start = start;
        this.end = end;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public Expression start() {
        return start;
    }

    public Expression end() {
        return end;
    }

    //

    @Override
    public Expression mirror() {
        return new SliceOfExpression(pos(),
                subject.mirror(),
                start.mirror(),
                end.mirror());
    }

    @Override
    public Expression mono(GenericMap gm) {
        return monoCopy(new SliceOfExpression(pos(),
                subject.mono(gm),
                start.mono(gm),
                end.mono(gm)), gm);
    }

    //
    @Override
    public String toString() {
        return subject + "[" + start + ":" + end + "]";
    }
}
