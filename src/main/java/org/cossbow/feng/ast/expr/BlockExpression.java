package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.stmt.Statement;
import org.cossbow.feng.util.Lazy;

import java.util.List;

public class BlockExpression extends PrimaryExpression implements Scope {
    private final List<Statement> block;
    private Expression result;

    public BlockExpression(Position pos,
                           List<Statement> block,
                           Expression result) {
        super(pos);
        this.block = block;
        this.result = result;
        resultType.set(result.resultType);
    }

    public List<Statement> block() {
        return block;
    }

    public Expression result() {
        return result;
    }

    public void result(Expression result) {
        this.result = result;
    }

    public boolean unbound() {
        return true;
    }

    //

    private List<Variable> stack = List.of();
    private Lazy<Expression> origin = Lazy.nil();

    public List<Variable> stack() {
        return stack;
    }

    public void stack(List<Variable> variables) {
        stack = List.copyOf(variables);
    }

    public Lazy<Expression> origin() {
        return origin;
    }

    //

    @Override
    public String toString() {
        return "{ ... ; " + result + '}';
    }
}
