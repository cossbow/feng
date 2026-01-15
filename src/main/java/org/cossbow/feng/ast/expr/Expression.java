package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

abstract
public class Expression extends Entity {

    public Expression(Position pos) {
        super(pos);
    }

    public boolean isFinal() {
        return false;
    }
}
