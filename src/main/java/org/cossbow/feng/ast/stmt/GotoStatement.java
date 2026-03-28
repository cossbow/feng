package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Lazy;

public class GotoStatement extends Statement {
    private final Identifier label;

    public GotoStatement(Position pos,
                         Identifier label) {
        super(pos);
        this.label = label;
    }

    public Identifier label() {
        return label;
    }

    public final Lazy<Label> target = Lazy.nil();
}
