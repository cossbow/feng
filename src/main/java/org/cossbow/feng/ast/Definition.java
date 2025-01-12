package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

import java.util.Optional;

abstract
public class Definition extends Entity {
    private final Modifier modifier;
    private final Optional<Identifier> name;
    private final TypeParameters generic;

    public Definition(Position pos,
                      Modifier modifier,
                      Optional<Identifier> name,
                      TypeParameters generic) {
        super(pos);
        this.modifier = modifier;
        this.name = name;
        this.generic = generic;
    }

    public Modifier modifier() {
        return modifier;
    }

    public Optional<Identifier> name() {
        return name;
    }

    public TypeParameters generic() {
        return generic;
    }

    public boolean unnamed() {
        return name.isEmpty();
    }
}
