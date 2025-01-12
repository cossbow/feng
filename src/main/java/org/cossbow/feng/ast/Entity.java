package org.cossbow.feng.ast;

abstract
public class Entity {
    private final Position pos;

    public Entity(Position pos) {
        this.pos = pos;
    }

    public Position pos() {
        return pos;
    }
}
