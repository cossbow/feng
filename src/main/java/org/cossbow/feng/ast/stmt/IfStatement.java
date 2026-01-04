package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class IfStatement extends Statement {
    private Optional<Statement> init;
    private Expression condition;
    private Statement yes;
    private Optional<Statement> not;

    public IfStatement(Position pos,
                       Optional<Statement> init,
                       Expression condition,
                       Statement yes,
                       Optional<Statement> not) {
        super(pos);
        this.init = init;
        this.condition = condition;
        this.yes = yes;
        this.not = not;
    }

    public Optional<Statement> init() {
        return init;
    }

    public Expression condition() {
        return condition;
    }

    public Statement yes() {
        return yes;
    }

    public Optional<Statement> not() {
        return not;
    }
}
