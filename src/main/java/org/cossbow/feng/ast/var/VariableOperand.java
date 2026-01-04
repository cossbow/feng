package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.SymbolExpression;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.util.Lazy;

public class VariableOperand extends Operand {
    private final Symbol symbol;

    public VariableOperand(Position pos,
                           Symbol symbol) {
        super(pos);
        this.symbol = symbol;
    }

    public Symbol symbol() {
        return symbol;
    }

    @Override
    public Expression rhs() {
        return new SymbolExpression(pos(), symbol, TypeArguments.EMPTY);
    }

    private final Lazy<Variable> variable = Lazy.nil();

    public Lazy<Variable> variable() {
        return variable;
    }
    //

    @Override
    public String toString() {
        return symbol.toString();
    }
}
