package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.stmt.BlockStatement;

import java.util.Set;

public class Procedure extends Entity {
    private final Prototype prototype;
    private final BlockStatement body;
    private final Set<Identifier> labels;

    public Procedure(Position pos,
                     Prototype prototype,
                     BlockStatement body,
                     Set<Identifier> labels) {
        super(pos);
        this.prototype = prototype;
        this.body = body;
        this.labels = labels;
    }

    public Prototype prototype() {
        return prototype;
    }

    public BlockStatement body() {
        return body;
    }

    public Set<Identifier> labels() {
        return labels;
    }

}
