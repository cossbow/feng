package org.cossbow.feng.ast;

import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.util.Lazy;

public class GlobalVariable extends Variable {
    private final Symbol symbol;

    public GlobalVariable(Variable v, Symbol symbol) {
        super(v.pos(), v.modifier(), v.declare(), v.name(), v.type(), v.value());
        this.symbol = symbol;
    }

    public Symbol symbol() {
        return symbol;
    }


    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GlobalVariable v))
            return false;
        return symbol.equals(v.symbol);
    }

    @Override
    public int hashCode() {
        return symbol.hashCode();
    }
}
