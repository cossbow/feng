package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.var.AssignableOperand;

import java.util.List;

public class AssignmentsStatement extends Statement {
    private final List<AssignableOperand> operands;
    private final Tuple tuple;

    public AssignmentsStatement(Position pos,
                                List<AssignableOperand> operands,
                                Tuple tuple) {
        super(pos);
        this.operands = operands;
        this.tuple = tuple;
    }

    public List<AssignableOperand> operands() {
        return operands;
    }

    public Tuple tuple() {
        return tuple;
    }
}
