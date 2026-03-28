package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;

public class LabeledStatement extends Statement {
    private final Label label;
    private final Statement target;

    public LabeledStatement(Position pos,
                            Label label,
                            Statement target) {
        super(pos);
        this.label = label;
        this.target = target;
    }

    public Label label() {
        return label;
    }

    public Statement target() {
        return target;
    }
}
