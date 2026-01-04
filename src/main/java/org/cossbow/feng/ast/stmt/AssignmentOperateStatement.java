package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.BinaryOperator;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.var.AssignableOperand;

public class AssignmentOperateStatement extends Statement {
    private AssignableOperand operand;
    private BinaryOperator operator;
    private Expression value;

    public AssignmentOperateStatement(Position pos,
                                      AssignableOperand operand,
                                      BinaryOperator operator,
                                      Expression value) {
        super(pos);
        this.operand = operand;
        this.operator = operator;
        this.value = value;
    }

    public AssignableOperand operand() {
        return operand;
    }

    public BinaryOperator operator() {
        return operator;
    }

    public Expression value() {
        return value;
    }
}
