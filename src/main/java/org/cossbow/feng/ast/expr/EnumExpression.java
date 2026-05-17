package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.EnumDefinition;
import org.cossbow.feng.ast.Position;

/**
 * The internal expressions of a compiler cannot be syntactically defined.
 * <p>
 * To facilitate the representation of enumeration values.
 */
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
