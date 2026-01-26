package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.UnaryOperator;

public class UnaryExpression extends Expression {
    private UnaryOperator operator;
    private Expression operand;

    public UnaryExpression(Position pos,
                           UnaryOperator operator,
                           Expression operand) {
        super(pos);
        this.operator = operator;
        this.operand = operand;
    }

    public UnaryOperator operator() {
        return operator;
    }

    public Expression operand() {
        return operand;
    }

    //

    @Override
    public String toString() {
        return operator.toString() + operand;
    }
}
