package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.Optional;

public class Attribute extends Entity {
    private final Identifier type;
    private final Optional<Expression> init;

    public Attribute(Position pos,
                     Identifier type,
                     Optional<Expression> init) {
        super(pos);
        this.type = type;
        this.init = init;
    }

    public Identifier type() {
        return type;
    }

    public Optional<Expression> init() {
        return init;
    }
}
