package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.proc.FunctionDefinition;

/**
 * Mainly divided into two branches:
 * {@link TypeDefinition} and {@link FunctionDefinition}.
 */
abstract
public class Definition extends Entity
        implements Exportable {
    private final Modifier modifier;
    /**
     * The defined name is converted into
     * a unified symbol during parsing
     */
    private final Symbol symbol;
    /**
     * Declared generic type parameters
     */
    private final TypeParameters generic;

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

    /**
     * Set when automatically creating builtin definitions
     */
    private boolean builtin;

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
