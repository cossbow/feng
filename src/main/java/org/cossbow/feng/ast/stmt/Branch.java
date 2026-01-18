package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

import java.util.List;

public class Branch extends Entity {
    private List<Statement> statements;

    public Branch(Position pos,
                  List<Statement> statements) {
        super(pos);
        this.statements = statements;
    }

    public List<Statement> statements() {
        return statements;
    }
}
