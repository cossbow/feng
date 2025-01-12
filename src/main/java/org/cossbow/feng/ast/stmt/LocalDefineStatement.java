package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Definition;
import org.cossbow.feng.ast.Position;

public class LocalDefineStatement extends Statement {
    private final Definition definition;

    public LocalDefineStatement(Position pos,
                                Definition definition) {
        super(pos);
        this.definition = definition;
    }

    public Definition definition() {
        return definition;
    }

}
