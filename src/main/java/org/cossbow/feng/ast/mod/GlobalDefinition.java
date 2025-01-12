package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Definition;
import org.cossbow.feng.ast.Position;

public class GlobalDefinition extends Global {
    private final Definition definition;

    public GlobalDefinition(Position pos, boolean export, Definition definition) {
        super(pos, export);
        this.definition = definition;
    }

    public Definition definition() {
        return definition;
    }
}
