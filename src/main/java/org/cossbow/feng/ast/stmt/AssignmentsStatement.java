package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.var.Operand;

import java.util.List;

public class AssignmentsStatement extends Statement {
    private List<Operand> operands;
    private List<Expression> values;

    private boolean copy;

    public AssignmentsStatement(Position pos,
                                List<Operand> operands,
                                List<Expression> values,
                                boolean copy) {
        super(pos);
        this.operands = operands;
        this.values = values;
        this.copy = copy;
    }

    public List<Operand> operands() {
        return operands;
    }

    public void operands(List<Operand> operands) {
        this.operands = operands;
    }

    public List<Expression> values() {
        return values;
    }

    public void values(List<Expression> tuple) {
        this.values = tuple;
    }

    public Operand operand(int i) {
        return operands.get(i);
    }

    public Expression value(int i) {
        return values.get(i);
    }

    public boolean copy() {
        return copy;
    }
}
