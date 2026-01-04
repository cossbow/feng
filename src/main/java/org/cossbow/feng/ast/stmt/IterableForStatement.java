package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.List;

public class IterableForStatement extends ForStatement {
    private List<Identifier> arguments;
    private Expression iterable;

    public IterableForStatement(Position pos, Statement body,
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
