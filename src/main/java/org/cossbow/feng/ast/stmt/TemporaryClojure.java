package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.Expression;

import java.util.List;

public class TemporaryClojure extends Statement {
    private List<Variable> relay;
    private Expression terminal;

    public TemporaryClojure(Position pos,
                            List<Variable> relay,
                            Expression terminal) {
        super(pos);
        this.relay = relay;
        this.terminal = terminal;
    }

    public List<Variable> relay() {
        return relay;
    }

    public Expression terminal() {
        return terminal;
    }

}
