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

    public Symbol type() {
        return type;
    }

    public Optional<ObjectExpression> init() {
        return init;
    }

    //
    @Override
    public String toString() {
        if (init.none()) return "@" + type;
        return "@" + type + '(' + init.get() + ')';
    }
}
