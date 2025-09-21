package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.NewType;

public class NewExpression extends PrimaryExpression {
    private final NewType type;
    private final Optional<Expression> init;

    public NewExpression(Position pos,
                         NewType type,
                         Optional<Expression> init) {
        super(pos);
        this.type = type;
        this.init = init;
    }

    public NewType type() {
        return type;
    }

    public Optional<Expression> init() {
        return init;
    }
}
