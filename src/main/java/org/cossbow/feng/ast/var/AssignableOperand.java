package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

abstract
public class AssignableOperand extends Entity {
    public AssignableOperand(Position pos) {
        super(pos);
    }

    abstract public Expression rhs();
}
