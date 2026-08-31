package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.Objects;

/**
 * Complement constraint {@code !C}: contains all types <em>not</em> in
 * the operand constraint.
 */
public class ExcludeTypeConstraint extends TypeConstraint {
    private final TypeConstraint operand;

    public ExcludeTypeConstraint(Position pos,
                                 TypeConstraint operand) {
        super(pos);
        this.operand = operand;
    }

    public TypeConstraint operand() {
        return operand;
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        return !operand.contains(t);
    }

    @Override
    public TypeView view() {
        // Complement cannot be represented as a single TypeView;
        // the most we can say is "unknown" — all we know is the
        // type is NOT in the operand set.
        return TypeView.unknown();
    }

    //

    @Override
    public final boolean equals(Object o) {
        return o instanceof ExcludeTypeConstraint c
                && operand.equals(c.operand);

    }

    @Override
    public int hashCode() {
        return operand.hashCode();
    }

    //
    @Override
    public String toString() {
        return "!" + operand;
    }
}
