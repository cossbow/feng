package org.cossbow.feng.ast;

import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

public class GlobalVariable extends Variable {
    private Symbol symbol;
    private Lazy<Expression> init;

    public GlobalVariable(Variable v, Symbol symbol, Optional<Expression> init) {
        super(v.pos(), v.modifier(), v.declare(), v.name(), v.type());
        this.symbol = symbol;
        this.init = Lazy.of(init);
    }

    public Symbol symbol() {
        return symbol;
    }

    public Lazy<Expression> init() {
        return init;
    }

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
