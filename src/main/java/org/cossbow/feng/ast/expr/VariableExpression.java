package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.GlobalVariable;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Optional;

/**
 * The internal type of the compiler cannot be defined syntactically.
 * <p>
 * When the analysis phase detects that a {@link SymbolExpression}
 * refers to a variable, a variable expression will be generated.
 */
public class VariableExpression extends PrimaryExpression {
    private final Variable variable;
    // After analysis, the symbol will be left blank
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
     * Mirror by creating a new Expression.
     */
    @Override
    public PrimaryExpression mirror() {
        // Global variables are under the module defined by the function.
        // If they are parsed and bound before expansion, they cannot be
        // parsed again, otherwise the variable cannot be found
        if (variable instanceof GlobalVariable)
            return this;
        // This method should not be called in subsequent processing as
        // it is only deployed before analysis.
        if (symbol.none())
            return ErrorUtil.unreachable();
        // Local variables need to be parsed again after expansion
        return new SymbolExpression(pos(), symbol.get(),
                TypeArguments.EMPTY);
    }

    @Override
    public PrimaryExpression mono(GenericMap gm) {
        return monoCopy(new VariableExpression(pos(), variable, symbol), gm);
    }

    //

    @Override
    public String toString() {
        return variable.name().toString();
    }
}
