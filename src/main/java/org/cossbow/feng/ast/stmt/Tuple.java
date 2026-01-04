package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

abstract
public class Tuple extends Entity {
    public Tuple(Position pos) {
        super(pos);
    }

    abstract
    public int size();

}
