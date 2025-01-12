package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

public class VariableAssignableOperand extends AssignableOperand {
    private final Identifier name;

    public VariableAssignableOperand(Position pos,
                                     Identifier name) {
        super(pos);
        this.name = name;
    }

    public Identifier name() {
        return name;
    }
}
