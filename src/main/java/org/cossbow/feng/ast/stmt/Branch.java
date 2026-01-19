package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

public class Branch extends Entity {
    private BlockStatement body;

    public Branch(Position pos,
                  BlockStatement body) {
        super(pos);
        this.body = body;
    }

    public BlockStatement body() {
        return body;
    }
}
