package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.EnumDefinition;
import org.cossbow.feng.ast.Position;

public class EnumValueExpression extends EnumExpression {
    private final EnumDefinition.Value value;

    public EnumValueExpression(Position pos,
                               EnumDefinition def,
                               EnumDefinition.Value value) {
        super(pos, def);
        this.value = value;
    }

    public EnumDefinition.Value value() {
        return value;
    }

    //

    @Override
    public String toString() {
        return def().symbol() + "." + value.name();
    }
}
