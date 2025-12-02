package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;

public class DefinedType extends Entity {
    private final Symbol symbol;
    private final TypeArguments generic;

    public DefinedType(Position pos,
                       Symbol symbol,
                       TypeArguments generic) {
        super(pos);
        this.symbol = symbol;
        this.generic = generic;
    }

    public Symbol symbol() {
        return symbol;
    }

    public TypeArguments generic() {
        return generic;
    }
}
