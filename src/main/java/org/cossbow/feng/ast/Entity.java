package org.cossbow.feng.ast;

import org.cossbow.feng.util.ErrorUtil;

abstract
public class Entity implements Cloneable {
    private Position pos;

    public Entity(Position pos) {
        this.pos = pos;
    }

    public Position pos() {
        return pos;
    }


    public Entity clone() {
        try {
            return (Entity) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new ErrorUtil.UnreachableException();
        }
    }

}
