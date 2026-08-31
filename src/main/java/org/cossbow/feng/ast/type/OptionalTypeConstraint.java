package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

/**
 * Constraint {@code ?}: the type argument must be an optional reference
 * (a reference that may be nil).
 */
public class OptionalTypeConstraint extends TypeConstraint {
    public OptionalTypeConstraint(Position pos) {
        super(pos);
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        var r = t.maybeRefer();
        return r.has() && !r.get().required();
    }

    @Override
    public TypeView view() {
        return TypeView.optional(true);
    }


    //

    @Override
    public boolean equals(Object c) {
        return c instanceof OptionalTypeConstraint;
    }

    @Override
    public int hashCode() {
        return 2000;
    }

    //
    @Override
    public String toString() {
        return "?";
    }
}
