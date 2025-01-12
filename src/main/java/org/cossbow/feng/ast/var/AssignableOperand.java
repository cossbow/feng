package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

abstract
public class AssignableOperand extends Entity {
    public AssignableOperand(Position pos) {
        super(pos);
    }
}
