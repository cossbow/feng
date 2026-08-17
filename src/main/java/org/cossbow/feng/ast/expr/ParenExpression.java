package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;

/**
 * {@code var v = (a);}
 */
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

    @Override
    public ParenExpression mirror() {
        return new ParenExpression(pos(), child.mirror());
    }

    @Override
    public ParenExpression mono(GenericMap gm) {
        return monoCopy(new ParenExpression(pos(), child.mono(gm)), gm);
    }

    //

    @Override
    public String toString() {
        return "(" + child + ")";
    }
}
