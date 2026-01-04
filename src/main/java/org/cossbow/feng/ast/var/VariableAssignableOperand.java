package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;

public class VariableAssignableOperand extends AssignableOperand {
    private Symbol symbol;

    public VariableAssignableOperand(Position pos,
                                     Symbol symbol) {
        super(pos);
        this.symbol = symbol;
    }

    public Symbol symbol() {
        return symbol;
    }
}
