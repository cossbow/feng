package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.stmt.Statement;

import java.util.List;

public class ClosureExpression extends PrimaryExpression implements Scope {
    private final List<Statement> block;
    private final Expression result;

    public ClosureExpression(Position pos,
                             List<Statement> block,
                             Expression result) {
        super(pos);
        this.block = block;
        this.result = result;
    }

    public List<Statement> block() {
        return block;
    }

    public Expression result() {
        return result;
    }

    //

    private volatile List<Variable> stack = List.of();

    public List<Variable> stack() {
        return stack;
    }

    public void stack(List<Variable> variables) {
        stack = variables;
    }

    //

    @Override
    public String toString() {
        return "{ ... ; " + result + ";}";
    }
}
