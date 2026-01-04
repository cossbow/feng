package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

abstract
public class Definition extends Entity {
    private Modifier modifier;
    private Optional<Identifier> name;
    private TypeParameters generic;

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

    public Identifier name() {
        return name.must();
    }

    public boolean named() {
        return name.has();
    }

    public boolean unnamed() {
        return name.none();
    }

    public TypeParameters generic() {
        return generic;
    }
}
