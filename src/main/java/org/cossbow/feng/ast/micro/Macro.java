package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

abstract
public class Macro extends Entity {
    private final Identifier type;

    public Macro(Position pos,
                 Identifier type) {
        super(pos);
        this.type = type;
    }

    public Identifier type() {
        return type;
    }

}
