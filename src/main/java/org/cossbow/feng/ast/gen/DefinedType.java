package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;

import java.util.Objects;

public class DefinedType extends Entity {
    private Symbol symbol;
    private TypeArguments generic;

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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefinedType that)) return false;
        return Objects.equals(symbol, that.symbol) &&
                Objects.equals(generic, that.generic);
    }

    @Override
    public String toString() {
        if (generic.isEmpty()) return symbol.toString();
        return symbol.toString() + '`' + generic + '`';
    }
}
