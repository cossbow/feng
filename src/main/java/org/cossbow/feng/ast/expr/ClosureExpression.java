package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.stmt.Statement;

import java.util.List;

public class ClosureExpression extends PrimaryExpression {
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

    @Override
    public String toString() {
        return "{ ... ; " + result + ";}";
    }
}
