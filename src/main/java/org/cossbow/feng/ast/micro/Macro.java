package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

abstract
public class Macro extends Entity {
    private final Identifier name;

    public Macro(Position pos,
                 Identifier name) {
        super(pos);
        this.name = name;
    }

    public Identifier name() {
        return name;
    }
}
