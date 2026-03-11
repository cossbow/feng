package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.Expression;

import java.util.ArrayList;
import java.util.List;

public class Assignment extends Entity {
    private Operand operand;
    private Expression value;

    public Assignment(Position pos,
                      Operand operand,
                      Expression value) {
        super(pos);
        this.operand = operand;
        this.value = value;
    }

    public Operand operand() {
        return operand;
    }

    public void operand(Operand operand) {
        this.operand = operand;
    }

    public Expression value() {
        return value;
    }

    public void value(Expression value) {
        this.value = value;
    }

    //

    private final List<Variable> relay = new ArrayList<>();

    public List<Variable> relay() {
        return relay;
    }

    public void relay(Variable v) {
        relay.add(v);
    }

    //
    @Override
    public String toString() {
        return operand + " = " + value;
    }
}
