package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;

public class DerivedType extends DefinedType {
    private final Symbol symbol;
    private final TypeArguments generic;

    public DerivedType(Position pos,
                       Symbol symbol,
                       TypeArguments generic) {
        super(pos, symbol.name());
        this.symbol = symbol;
        this.generic = generic;
    }

    public Symbol symbol() {
        return symbol;
    }

    public TypeArguments generic() {
        return generic;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DerivedType t))
            return false;
        return symbol.equals(t.symbol) &&
                generic.equals(t.generic);
    }

}
