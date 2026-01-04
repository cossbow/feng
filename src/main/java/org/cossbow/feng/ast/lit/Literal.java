package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.ast.expr.LiteralExpression;
import org.cossbow.feng.util.Optional;

abstract
public class Literal extends Entity {
    public Literal(Position pos) {
        super(pos);
    }

    abstract
    public String type();

    public Optional<Primitive> compatible() {
        return Optional.empty();
    }

    public LiteralExpression expr() {
        var e = new LiteralExpression(pos(), this);
        e.resultType.set(Primitive.INT.declarer(pos()));
        return e;
    }

    abstract
    public boolean equals(Object o);

    abstract
    public int hashCode();

}
