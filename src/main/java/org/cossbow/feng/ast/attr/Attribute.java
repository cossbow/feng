package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.expr.ObjectExpression;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class Attribute extends Entity {
    private Symbol type;
    private Optional<ObjectExpression> init;

    public Attribute(Position pos,
                     Symbol type,
                     Optional<ObjectExpression> init) {
        super(pos);
        this.type = type;
        this.init = init;
    }

    public Attribute(Symbol type) {
        this(Position.ZERO, type, Optional.empty());
    }

    public Symbol type() {
        return type;
    }

    public Optional<ObjectExpression> init() {
        return init;
    }

    //
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Attribute a)) return false;
        return type.equals(a.type);
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public String toString() {
        if (init.none()) return "@" + type;
        return "@" + type + '(' + init.get() + ')';
    }
}
