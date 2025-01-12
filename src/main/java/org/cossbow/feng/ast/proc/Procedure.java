package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.stmt.BlockStatement;

public class Procedure extends Entity {
    private final Prototype prototype;
    private final BlockStatement body;

    public Procedure(Position pos,
                     Prototype prototype,
                     BlockStatement body) {
        super(pos);
        this.prototype = prototype;
        this.body = body;
    }

    public Prototype prototype() {
        return prototype;
    }

    public BlockStatement body() {
        return body;
    }
}
