package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class Assignment extends Entity {
    private Operand operand;
    private Expression value;
    private boolean copy;

    public Assignment(Position pos,
                      Operand operand,
                      Expression value,
                      boolean copy) {
        super(pos);
        this.operand = operand;
        this.value = value;
        this.copy = copy;
    }

    public Operand operand() {
        return operand;
    }

    public Expression value() {
        return value;
    }

    public boolean copy() {
        return copy;
    }
}
