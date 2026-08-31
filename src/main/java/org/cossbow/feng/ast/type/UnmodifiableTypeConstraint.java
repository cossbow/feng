package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

/**
 * Constraint {@code #}: the type argument must be an unmodifiable
 * reference (cannot modify the referenced instance).
 */
public class UnmodifiableTypeConstraint extends TypeConstraint {
    public UnmodifiableTypeConstraint(Position pos) {
        super(pos);
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        var r = t.maybeRefer();
        return r.has() && r.get().unmodifiable();
    }

    @Override
    public TypeView view() {
        return TypeView.unmodifiable(true);
    }

    //

    @Override
    public boolean equals(Object c) {
        return c instanceof UnmodifiableTypeConstraint;
    }

    @Override
    public int hashCode() {
        return 3000;
    }

    //
    @Override
    public String toString() {
        return "#";
    }
}
