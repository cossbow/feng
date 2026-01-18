package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;

import java.util.List;

public class BlockStatement extends Statement {
    private List<Statement> list;
    private boolean newScope;

    public BlockStatement(Position pos,
                          List<Statement> list) {
        this(pos, list, true);
    }

    public BlockStatement(Position pos,
                          List<Statement> list,
                          boolean newScope) {
        super(pos);
        this.list = list;
        this.newScope = newScope;
    }

    public List<Statement> list() {
        return list;
    }

    public boolean newScope() {
        return newScope;
    }

    //

    public BlockStatement unscope() {
        return new BlockStatement(pos(), list, false);
    }

}
