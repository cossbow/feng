package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.util.Optional;

public class ContinueStatement extends Statement {
    private final Optional<Identifier> label;

    public ContinueStatement(Position pos,
                             Optional<Identifier> label) {
        super(pos);
        this.label = label;
    }

    public Optional<Identifier> label() {
        return label;
    }

    @Override
    public ContinueStatement mirror() {
        return this;
    }

    @Override
    public ContinueStatement mono(GenericMap gm) {
        return this;
    }
}
