package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.util.Optional;

/**
 * The internal type of the compiler cannot be defined syntactically.
 * <p>
 * When the analysis phase detects that a {@link SymbolExpression}
 * refers to a variable, a variable expression will be generated.
 */
public class VariableExpression extends PrimaryExpression {
    private final Variable variable;
    private final Optional<Symbol> symbol;

    public VariableExpression(Position pos, Variable variable,
                              Optional<Symbol> symbol) {
        super(pos);
        this.variable = variable;
        this.symbol = symbol;
        this.resultType.set(variable.type());
    }

    public VariableExpression(Position pos, Variable variable,
                              Symbol symbol) {
        this(pos, variable, Optional.of(symbol));
    }

    public VariableExpression(Position pos, Variable variable) {
        this(pos, variable, Optional.empty());
    }

    public Variable variable() {
        return variable;
    }

    public Optional<Symbol> symbol() {
        return symbol;
    }

    //

    @Override
    public String toString() {
        return variable.name().toString();
    }
}
