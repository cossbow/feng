package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.util.Lazy;

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

    @Override
    public ThrowStatement mirror() {
        return new ThrowStatement(pos(), exception.mirror());
    }

    @Override
    public ThrowStatement mono(GenericMap gm) {
        var r = new ThrowStatement(pos(), exception.mono(gm));
        // mono2 重建函数体时必须保留语义分析挂上的 procedure / local 引用
        r.procedure.set(procedure);
        r.local(local);
        return r;
    }
}
