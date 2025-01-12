package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;

import java.util.List;

public class BlockStatement extends Statement {
    private final List<Statement> list;

    public BlockStatement(Position pos,
                          List<Statement> list) {
        super(pos);
        this.list = list;
    }

    public List<Statement> list() {
        return list;
    }
}
