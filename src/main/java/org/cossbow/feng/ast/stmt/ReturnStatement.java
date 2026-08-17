package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.gen.GenericMap;
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

    //

    @Override
    public ReturnStatement mirror() {
        var r = result.map(Expression::mirror);
        return new ReturnStatement(pos(), r);
    }

    @Override
    public ReturnStatement mono(GenericMap gm) {
        var r = new ReturnStatement(pos(), result.map(e -> e.mono(gm)));
        // mono2 重建函数体时必须保留语义分析挂上的 procedure / local 引用
        // （后端发射 return 时读 procedure 取返回类型，丢失会 NoSuchElementException）
        r.procedure.set(procedure);
        r.local(local);
        return r;
    }

    //
    @Override
    public String toString() {
        return "return " + result + ';';
    }
}
