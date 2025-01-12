package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

public class DefinedType extends Entity {
    private final Identifier name;
    private final TypeArguments generic;

    public DefinedType(Position pos,
                       Identifier name,
                       TypeArguments generic) {
        super(pos);
        this.name = name;
        this.generic = generic;
    }

    public Identifier name() {
        return name;
    }

    public TypeArguments generic() {
        return generic;
    }
}
