package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

abstract
public class Literal extends Entity {
    public Literal(Position pos) {
        super(pos);
    }

    abstract
    public String type();

    abstract
    public boolean equals(Object o);

}
