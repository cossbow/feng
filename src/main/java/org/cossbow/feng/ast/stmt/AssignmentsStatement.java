package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.var.AssignableOperand;

import java.util.List;

public class AssignmentsStatement extends Statement {
    private final List<AssignableOperand> operands;
    private final Tuple tuple;
    private final boolean copy;

    public AssignmentsStatement(Position pos,
                                List<AssignableOperand> operands,
                                Tuple tuple,
                                boolean copy) {
        super(pos);
        this.operands = operands;
        this.tuple = tuple;
        this.copy = copy;
    }

    public List<AssignableOperand> operands() {
        return operands;
    }

    public Tuple tuple() {
        return tuple;
    }

    public boolean copy() {
        return copy;
    }
}
