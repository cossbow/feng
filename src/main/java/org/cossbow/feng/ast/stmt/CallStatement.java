package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.CallExpression;

public class CallStatement extends Statement {
    private final CallExpression call;

    public CallStatement(Position pos,
                         CallExpression call) {
        super(pos);
        this.call = call;
    }

    public CallExpression call() {
        return call;
    }
}
