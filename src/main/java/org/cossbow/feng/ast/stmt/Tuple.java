package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.List;

public class Tuple extends Entity {
    private List<Expression> values;

    public Tuple(Position pos,
                 List<Expression> values) {
        super(pos);
        this.values = values;
    }

    public List<Expression> values() {
        return values;
    }

    public int size() {
        return values.size();
    }

    //

    @Override
    public String toString() {
        return "(" + values + ")";
    }

}
