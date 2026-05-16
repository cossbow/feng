package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.lit.Literal;

/**
 *
 */
public class LiteralExpression extends PrimaryExpression {
    private final Literal literal;

    public LiteralExpression(Position pos, Literal literal) {
        super(pos);
        this.literal = literal;
    }

    public LiteralExpression(Literal literal) {
        this(literal.pos(), literal);
    }

    public Literal literal() {
        return literal;
    }

    public TypeDeclarer type() {
        return expectType.has() ?
                expectType.must() :
                resultType.must();
    }

    @Override
    public boolean unbound() {
        return true;
    }

    //
    @Override
    public String toString() {
        return literal.toString();
    }

}
