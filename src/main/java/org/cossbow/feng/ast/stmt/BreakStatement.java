package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;

public class BreakStatement extends Statement {
    private final Optional<Identifier> label;

    public BreakStatement(Position pos,
                          Optional<Identifier> label) {
        super(pos);
        this.label = label;
    }

    public Optional<Identifier> label() {
        return label;
    }
}
