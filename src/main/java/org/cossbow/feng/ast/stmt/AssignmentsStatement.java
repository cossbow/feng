package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.var.Assignment;
import org.cossbow.feng.ast.var.Operand;

import java.util.List;

public class AssignmentsStatement extends Statement {
    private List<Assignment> list;

    public AssignmentsStatement(Position pos,
                                List<Assignment> list) {
        super(pos);
        this.list = list;
    }

    public List<Assignment> list() {
        return list;
    }

    public Assignment get(int i) {
        return list.get(i);
    }

    public Operand operand(int i) {
        return list.get(i).operand();
    }

    public Expression value(int i) {
        return list.get(i).value();
    }

}
