package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.CallExpression;

public class CallStatement extends Statement {
    private CallExpression call;

    public CallStatement(Position pos,
                         CallExpression call) {
        super(pos);
        this.call = call;
    }

    public CallExpression call() {
        return call;
    }

    public void call(CallExpression call) {
        this.call = call;
    }
}
