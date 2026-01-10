package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

public class CurrentExpression extends Expression {
    private boolean isSelf;

    public CurrentExpression(Position pos, boolean isSelf) {
        super(pos);
        this.isSelf = isSelf;
    }

}
