package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;

abstract
public class PrimaryExpression extends Expression {
    public PrimaryExpression(Position pos) {
        super(pos);
    }

    abstract
    public PrimaryExpression mirror();

    @Override
    public abstract PrimaryExpression mono(GenericMap gm);
}
