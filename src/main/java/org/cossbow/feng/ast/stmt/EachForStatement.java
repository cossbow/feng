package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.List;

public class EachForStatement extends ForStatement {
    private final List<Identifier> arguments;
    private final Expression iterable;

    public EachForStatement(Position pos, Statement body,
                            List<Identifier> arguments,
                            Expression iterable) {
        super(pos, body);
        this.arguments = arguments;
        this.iterable = iterable;
    }

    public List<Identifier> arguments() {
        return arguments;
    }

    public Expression iterable() {
        return iterable;
    }
}
