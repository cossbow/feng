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

    /**
     * Mirror by creating a new VariableExpression node.
     * <p>
     * The variable and symbol references are kept as-is (shared).
     * This works because:
     * <ul>
     *   <li>Global variables (like stdout) — the Variable object is globally
     *       shared and its type/value are already fully analyzed.</li>
     *   <li>Parameter variables (like fmt) — the original Variable has
     *       value=nil, so resolveFormatString falls through to
     *       context.findVar(symbol) which finds the mirror variable
     *       created by expandInlined in the current scope.</li>
     * </ul>
     * The key benefit: a new Expression node gets fresh
     * expectType/resultType/expectCallable state, independent
     * from other expansion copies.
     */
    @Override
    public VariableExpression mirror() {
        return this;
    }

    //

    @Override
    public String toString() {
        return variable.name().toString();
    }
}
