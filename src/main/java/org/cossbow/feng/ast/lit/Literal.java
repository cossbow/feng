package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Primitive;

abstract
public class Literal extends Entity {
    public Literal(Position pos) {
        super(pos);
    }

    abstract
    public String type();

    public boolean compatible(Primitive primitive) {
        return false;
    }

    abstract
    public boolean equals(Object o);

}
