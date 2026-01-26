package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.EnumDefinition;
import org.cossbow.feng.ast.Position;

public class EnumIdExpression extends EnumExpression {
    private final Expression index;

    public EnumIdExpression(Position pos,
                            EnumDefinition def,
                            Expression index) {
        super(pos, def);
        this.index = index;
    }

    public Expression index() {
        return index;
    }

    //

    @Override
    public String toString() {
        return def().symbol() + "[" + index + "]";
    }
}
