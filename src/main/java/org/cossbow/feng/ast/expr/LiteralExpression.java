package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.lit.Literal;

public class LiteralExpression extends PrimaryExpression {
    private final Literal literal;

    public LiteralExpression(Position pos, Literal literal) {
        super(pos);
        this.literal = literal;
    }

    public Literal literal() {
        return literal;
    }

    @Override
    public String toString() {
        return literal.toString();
    }

}
