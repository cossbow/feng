package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.util.ErrorUtil;

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

    @Override
    public LabeledStatement mirror() {
        return ErrorUtil.unsupported("mirror not support label");
    }

    @Override
    public LabeledStatement mono(GenericMap gm) {
        return new LabeledStatement(pos(), label, target.mono(gm));
    }
}
