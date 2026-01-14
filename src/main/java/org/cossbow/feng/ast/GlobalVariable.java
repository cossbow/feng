package org.cossbow.feng.ast;

import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Optional;

public class GlobalVariable extends Variable {
    private Symbol symbol;
    private Optional<Expression> init;

    public GlobalVariable(Variable v, Symbol symbol, Optional<Expression> init) {
        super(v.pos(), v.modifier(), v.declare(), v.name(), v.type());
        this.symbol = symbol;
        this.init = init;
    }

    public Symbol symbol() {
        return symbol;
    }

    public Optional<Expression> init() {
        return init;
    }
}
