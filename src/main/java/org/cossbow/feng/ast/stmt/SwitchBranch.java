package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Lazy;

import java.util.List;

public class SwitchBranch extends Branch {
    private List<Expression> constants;
    private boolean fallthrough;

    public SwitchBranch(Position pos,
                        List<Expression> constants,
                        List<Statement> statements,
                        boolean fallthrough) {
        super(pos, statements);
        this.constants = constants;
        this.fallthrough = fallthrough;
    }

    public List<Expression> constants() {
        return constants;
    }

    public boolean fallthrough() {
        return fallthrough;
    }

    //

}
