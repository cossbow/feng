package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.EnumDefinition;
import org.cossbow.feng.ast.Position;

/**
 * The internal expressions of a compiler cannot be syntactically defined.
 * <p>
 * Get the enumeration value based on the id:
 * For the type {@code enum State{S1,S2,} }
 * <p>
 * we can use value by id: {@code var s = State[0];}
 */
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
