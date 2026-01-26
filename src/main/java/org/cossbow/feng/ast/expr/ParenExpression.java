package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

public class ParenExpression extends PrimaryExpression {
    private Expression child;

    public ParenExpression(Position pos,
                           Expression child) {
        super(pos);
        this.child = child;
    }

    public Expression child() {
        return child;
    }

    @Override
    public boolean unbound() {
        return child.unbound();
    }

    //

    @Override
    public String toString() {
        return "(" + child + ")";
    }
}
