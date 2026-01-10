package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.List;

public class SwitchStatement extends Statement {
    private Optional<Statement> init;
    private Expression value;
    private List<SwitchBranch> branches;
    private List<Statement> defaultBranch;

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
