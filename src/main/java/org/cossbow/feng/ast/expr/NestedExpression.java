package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

abstract
public class NestedExpression extends PrimaryExpression {
    public NestedExpression(Position pos) {
        super(pos);
    }

    abstract
    public PrimaryExpression subject();

}
