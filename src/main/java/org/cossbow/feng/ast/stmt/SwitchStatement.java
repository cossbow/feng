package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.List;
import java.util.Optional;

public class SwitchStatement extends Statement {
    private final Optional<Statement> init;
    private final Expression value;
    private final List<SwitchBranch> branches;
    private final List<Statement> defaultBranch;

    public SwitchStatement(Position pos,
                           Optional<Statement> init,
                           Expression value,
                           List<SwitchBranch> branches,
                           List<Statement> defaultBranch) {
        super(pos);
        this.init = init;
        this.value = value;
        this.branches = branches;
        this.defaultBranch = defaultBranch;
    }

    public Optional<Statement> init() {
        return init;
    }

    public Expression value() {
        return value;
    }

    public List<SwitchBranch> branches() {
        return branches;
    }

    public List<Statement> defaultBranch() {
        return defaultBranch;
    }
}
