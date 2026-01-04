package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;

abstract
public class DefinedType extends Entity {
    private final Identifier name;

    public DefinedType(Position pos,
                       Identifier name) {
        super(pos);
        this.name = name;
    }

    public Identifier name() {
        return name;
    }

    @Override
    abstract
    public boolean equals(Object o);

    @Override
    abstract
    public int hashCode();

}
