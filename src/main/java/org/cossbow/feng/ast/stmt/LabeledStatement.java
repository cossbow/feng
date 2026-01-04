package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

public class LabeledStatement extends Statement {
    private Identifier label;
    private Statement statement;

    public LabeledStatement(Position pos,
                            Identifier label,
                            Statement statement) {
        super(pos);
        this.label = label;
        this.statement = statement;
    }

    public Identifier label() {
        return label;
    }

    public Statement statement() {
        return statement;
    }
}
