package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class Declaration extends Entity {
    private Variable variable;
    private Optional<Expression> init;

    public Declaration(Position pos) {
        super(pos);
    }

    public Variable variable() {
        return variable;
    }

    public Optional<Expression> init() {
        return init;
    }

}
