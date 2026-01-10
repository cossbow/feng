package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;

import java.util.List;

public class TryStatement extends Statement {
    private BlockStatement body;
    private List<CatchClause> catchClauses;
    private Optional<BlockStatement> finallyClause;

    public TryStatement(Position pos,
                        BlockStatement body,
                        List<CatchClause> catchClauses,
                        Optional<BlockStatement> finallyClause) {
        super(pos);
        this.body = body;
        this.catchClauses = catchClauses;
        this.finallyClause = finallyClause;
    }

    public BlockStatement body() {
        return body;
    }

    public List<CatchClause> catchClauses() {
        return catchClauses;
    }

    public Optional<BlockStatement> finallyClause() {
        return finallyClause;
    }
}
