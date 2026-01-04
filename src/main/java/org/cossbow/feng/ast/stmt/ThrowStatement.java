package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class ThrowStatement extends Statement {
    private Expression exception;

    public ThrowStatement(Position pos,
                          Expression exception) {
        super(pos);
        this.exception = exception;
    }

    public Expression exception() {
        return exception;
    }
}
