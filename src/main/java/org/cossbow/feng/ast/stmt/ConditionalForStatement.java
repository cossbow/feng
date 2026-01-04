package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class ConditionalForStatement extends ForStatement {
    private Optional<Statement> initializer;
    private Expression condition;
    private Optional<Statement> updater;

    public ConditionalForStatement(Position pos,
                                   Statement body,
                                   Optional<Statement> initializer,
                                   Expression condition,
                                   Optional<Statement> updater) {
        super(pos, body);
        this.initializer = initializer;
        this.condition = condition;
        this.updater = updater;
    }

    public Optional<Statement> initializer() {
        return initializer;
    }

    public Expression condition() {
        return condition;
    }

    public Optional<Statement> updater() {
        return updater;
    }
}
