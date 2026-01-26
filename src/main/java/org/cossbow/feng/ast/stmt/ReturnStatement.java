package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.List;

public class ReturnStatement extends Statement {
    private Optional<Expression> result;

    public ReturnStatement(Position pos,
                           Optional<Expression> result) {
        super(pos);
        this.result = result;
    }

    public Optional<Expression> result() {
        return result;
    }

    public void result(Optional<Expression> result) {
        this.result = result;
    }

    //

    private final Lazy<Procedure> procedure = Lazy.nil();
    private List<Variable> local = List.of();

    public Lazy<Procedure> procedure() {
        return procedure;
    }

    public List<Variable> local() {
        return local;
    }

    public void local(List<Variable> local) {
        this.local = local;
    }
}
