package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

abstract
public class Definition extends Entity {
    private Modifier modifier;
    private Symbol symbol;
    private TypeParameters generic;

    public Definition(Position pos,
                      Modifier modifier,
                      Symbol symbol,
                      TypeParameters generic) {
        super(pos);
        this.modifier = modifier;
        this.symbol = symbol;
        this.generic = generic;
    }

    public Modifier modifier() {
        return modifier;
    }

    public Symbol symbol() {
        return symbol;
    }

    public TypeParameters generic() {
        return generic;
    }

    //

    private volatile boolean builtin;

    public boolean builtin() {
        return builtin;
    }

    public void builtin(boolean builtin) {
        this.builtin = builtin;
    }
}
