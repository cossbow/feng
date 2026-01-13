package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

abstract
public class Definition extends Entity {
    private Modifier modifier;
    private Identifier name;
    private TypeParameters generic;

    public Definition(Position pos,
                      Modifier modifier,
                      Identifier name,
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
        return name;
    }

    public TypeParameters generic() {
        return generic;
    }
}
