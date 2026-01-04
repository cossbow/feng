package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

public class ReferEqualExpression extends PrimaryExpression {
    private final PrimaryExpression left, right;
    private final boolean same;

    public ReferEqualExpression(Position pos,
                                PrimaryExpression left,
                                PrimaryExpression right,
                                boolean same) {
        super(pos);
        this.left = left;
        this.right = right;
        this.same = same;
    }

    public PrimaryExpression left() {
        return left;
    }

    public PrimaryExpression right() {
        return right;
    }

    public boolean same() {
        return same;
    }
}
