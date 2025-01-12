package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.List;

public class ArrayTuple extends Tuple {
    private final List<Expression> values;

    public ArrayTuple(Position pos,
                      List<Expression> values) {
        super(pos);
        this.values = values;
    }

    public List<Expression> values() {
        return values;
    }
}
