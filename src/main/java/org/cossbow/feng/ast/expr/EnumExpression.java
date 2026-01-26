package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.EnumDefinition;
import org.cossbow.feng.ast.Position;

abstract
public class EnumExpression extends PrimaryExpression {
    private final EnumDefinition def;

    public EnumExpression(Position pos,
                          EnumDefinition def) {
        super(pos);
        this.def = def;
    }

    public EnumDefinition def() {
        return def;
    }

}
