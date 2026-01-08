package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.CallExpression;

public class ReturnTuple extends Tuple {
    private CallExpression call;

    public ReturnTuple(Position pos,
                       CallExpression call) {
        super(pos);
        this.call = call;
    }

    public CallExpression call() {
        return call;
    }

    @Override
    public int size() {
        return 0;
    }

}
