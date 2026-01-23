package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.ReferExpression;
import org.cossbow.feng.ast.gen.TypeArguments;

public class VariableAssignableOperand extends AssignableOperand {
    private final Symbol symbol;

    public VariableAssignableOperand(Position pos,
                                     Symbol symbol) {
        super(pos);
        this.symbol = symbol;
    }

    public Symbol symbol() {
        return symbol;
    }

    @Override
    public Expression rhs() {
        return new ReferExpression(pos(), symbol, TypeArguments.EMPTY);
    }

    //

    @Override
    public String toString() {
        return symbol.toString();
    }
}
