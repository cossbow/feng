package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.Objects;

/**
 * Parenthesized constraint {@code (C)}: delegates to the child constraint.
 */
public class ParenTypeConstraint extends TypeConstraint {
    private final TypeConstraint child;

    public ParenTypeConstraint(Position pos,
                               TypeConstraint child) {
        super(pos);
        this.child = child;
    }

    public TypeConstraint child() {
        return child;
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        return child.contains(t);
    }

    @Override
    public TypeView view() {
        return child.view();
    }

    //


    @Override
    public final boolean equals(Object o) {
        return o instanceof ParenTypeConstraint c
                && child.equals(c.child);
    }

    @Override
    public int hashCode() {
        return child.hashCode();
    }

    //
    @Override
    public String toString() {
        return "(" + child + ")";
    }
}
