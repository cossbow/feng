package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.lit.Literal;
import org.cossbow.feng.util.Lazy;

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

    @Override
    public boolean unbound() {
        return true;
    }

    public final Lazy<TypeDeclarer> lt = Lazy.nil();

    //
    @Override
    public String toString() {
        return literal.toString();
    }

}
