package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.EnumDefinition;
import org.cossbow.feng.ast.Position;

/**
 * The internal expressions of a compiler cannot be syntactically defined.
 * <p>
 * Get the enumeration value based on the id:
 * * For the type {@code enum State{S1,S2,} }
 * * <p>
 * * we can use value by name: {@code var s = State.S1;}
 */
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
