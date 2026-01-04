package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

public class LabeledStatement extends Statement {
    private final Identifier label;
    private final Statement target;

    public LabeledStatement(Position pos,
                            Identifier label,
                            Statement target) {
        super(pos);
        this.label = label;
        this.target = target;
    }

    public Identifier label() {
        return label;
    }

    public Statement target() {
        return target;
    }
}
