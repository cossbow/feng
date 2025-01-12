package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.List;

public class SwitchBranch extends Entity {
    private final List<Expression> constants;
    private final List<Statement> statements;
    private final boolean fallthrough;

    public SwitchBranch(Position pos,
                        List<Expression> constants,
                        List<Statement> statements,
                        boolean fallthrough) {
        super(pos);
        this.constants = constants;
        this.statements = statements;
        this.fallthrough = fallthrough;
    }

    public List<Expression> constants() {
        return constants;
    }

    public List<Statement> statements() {
        return statements;
    }

    public boolean fallthrough() {
        return fallthrough;
    }
}
