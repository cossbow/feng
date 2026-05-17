package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;

/**
 * The internal type of the compiler cannot be defined syntactically.
 * <p>
 * When the analysis phase detects that a {@link SymbolExpression}
 * refers to a variable, a variable expression will be generated.
 */
public class VariableExpression extends PrimaryExpression {
    private final Variable variable;

    public VariableExpression(Position pos, Variable variable) {
        super(pos);
        this.variable = variable;
        this.resultType.set(variable.type());
    }

    public Variable variable() {
        return variable;
    }

    //

    @Override
    public String toString() {
        return variable.name().toString();
    }
}
