package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.proc.Procedure;

public class LambdaExpression extends PrimaryExpression {
    private final Procedure procedure;

    public LambdaExpression(Position pos,
                            Procedure procedure) {
        super(pos);
        this.procedure = procedure;
    }

    public Procedure procedure() {
        return procedure;
    }

    @Override
    public boolean isFinal() {
        return true;
    }
}
