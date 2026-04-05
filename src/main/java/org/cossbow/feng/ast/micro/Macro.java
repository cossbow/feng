package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;

import java.util.Objects;

abstract
public class Macro extends Entity {
    private Modifier modifier;
    private Identifier type;

    public Macro(Position pos,
                 Modifier modifier,
                 Identifier type) {
        super(pos);
        this.modifier = modifier;
        this.type = type;
    }

    public Modifier modifier() {
        return modifier;
    }

    public Identifier type() {
        return type;
    }

    abstract
    public Identifier name();

    public Identifier makeId() {
        return new Identifier("feng$macro$" + type() + "$" + name());
    }

    //


    @Override
    public boolean equals(Object o) {
        return o instanceof Macro m &&
                type.equals(m.type) &&
                name().equals(m.name());
    }

    @Override
    public int hashCode() {
        return type.hashCode() * 31 +
                name().hashCode();
    }

    //
    public String toString() {
        return type + ":" + name();
    }
}
