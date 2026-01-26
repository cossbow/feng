package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.util.Lazy;

import java.util.ArrayList;
import java.util.List;

public class ThrowStatement extends Statement {
    private Expression exception;

    public ThrowStatement(Position pos,
                          Expression exception) {
        super(pos);
        this.exception = exception;
    }

    public Expression exception() {
        return exception;
    }

    public void exception(Expression exception) {
        this.exception = exception;
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
