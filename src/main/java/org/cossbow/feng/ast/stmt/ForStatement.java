package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;

abstract
public class ForStatement extends Statement {
    private final Statement body;

    public ForStatement(Position pos, Statement body) {
        super(pos);
        this.body = body;
    }

    public Statement body() {
        return body;
    }
}
