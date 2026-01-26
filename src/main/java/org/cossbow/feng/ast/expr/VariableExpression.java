package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;

public class VariableExpression extends PrimaryExpression {
    private final Variable variable;

    public VariableExpression(Position pos, Variable variable) {
        super(pos);
        this.variable = variable;
    }

    public Variable variable() {
        return variable;
    }

    //

    @Override
    public String toString() {
        return variable.toString();
    }
}
