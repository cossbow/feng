package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class AttributeField extends Entity {
    private Identifier name;
    private Identifier type;
    private boolean array;
    private Optional<Expression> init;

    public AttributeField(Position pos,
                          Identifier name,
                          Identifier type,
                          boolean array,
                          Optional<Expression> init) {
        super(pos);
        this.name = name;
        this.type = type;
        this.array = array;
        this.init = init;
    }

    public Identifier name() {
        return name;
    }

    public Identifier type() {
        return type;
    }

    public boolean array() {
        return array;
    }

    public Optional<Expression> init() {
        return init;
    }
}
