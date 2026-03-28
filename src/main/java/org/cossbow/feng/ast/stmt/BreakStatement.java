package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

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

    public final Lazy<ForStatement> target = Lazy.nil();
}
