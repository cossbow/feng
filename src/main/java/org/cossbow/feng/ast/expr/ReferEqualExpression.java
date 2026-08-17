package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;

/**
 * The internal type of the compiler cannot be defined syntactically.
 * <p>
 * Used to compare whether two references are identical.
 */
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

    @Override
    public ReferEqualExpression mirror() {
        return new ReferEqualExpression(pos(),
                left.mirror(), right.mirror(), same);
    }

    @Override
    public ReferEqualExpression mono(GenericMap gm) {
        return monoCopy(new ReferEqualExpression(pos(),
                left.mono(gm), right.mono(gm), same), gm);
    }

    //
    @Override
    public String toString() {
        return left + (same ? " == " : " != ") + right;
    }
}
