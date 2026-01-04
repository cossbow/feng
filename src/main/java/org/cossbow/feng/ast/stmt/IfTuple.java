package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class IfTuple extends Tuple {
    private Expression condition;
    private Tuple yes, not;

    public IfTuple(Position pos,
                   Expression condition,
                   Tuple yes,
                   Tuple not) {
        super(pos);
        this.condition = condition;
        this.yes = yes;
        this.not = not;
    }

    public Expression condition() {
        return condition;
    }

    public Tuple yes() {
        return yes;
    }

    public Tuple not() {
        return not;
    }

    @Override
    public int size() {
        return yes.size();
    }
}
