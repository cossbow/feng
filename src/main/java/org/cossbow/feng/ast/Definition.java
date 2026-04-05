package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.Lazy;

import java.util.HashMap;
import java.util.Map;

abstract
public class Definition extends Entity
        implements Exportable {
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

    public boolean export() {
        return modifier.export();
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

    private boolean builtin;
    public final Map<TypeArguments, Definition> instantiated = new HashMap<>();
    public final Lazy<Definition> template = Lazy.nil();

    public boolean builtin() {
        return builtin;
    }

    public void builtin(boolean builtin) {
        this.builtin = builtin;
    }

    //

    public boolean equals(Object o) {
        if (!(o instanceof Definition t))
            return false;
        return symbol.equals(t.symbol);
    }

    public int hashCode() {
        return symbol.hashCode();
    }

}
