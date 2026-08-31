package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

/**
 * Constraint that matches reference types ({@code *any}).
 * <p>
 * A type satisfies this constraint if it is a reference type
 * ({@link TypeDeclarer#maybeRefer()} returns present).
 */
public class ReferTypeConstraint extends TypeConstraint {
    public ReferTypeConstraint(Position pos) {
        super(pos);
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        return t.maybeRefer().has();
    }

    @Override
    public TypeView view() {
        return TypeView.refer(true);
    }

    //

    @Override
    public boolean equals(Object c) {
        return c instanceof ReferTypeConstraint;
    }

    @Override
    public int hashCode() {
        return 1000;
    }

    //
    @Override
    public String toString() {
        return "*";
    }
}
