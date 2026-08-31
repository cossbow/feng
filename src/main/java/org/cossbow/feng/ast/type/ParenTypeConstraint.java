package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.EnumSet;

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
        if (t instanceof GenericTypeDeclarer gtd) {
            return containsByConstraint(gtd);
        }
        return child.contains(t);
    }

    @Override
    public boolean include(TypeConstraint tc) {
        return child.include(tc);
    }

    @Override
    public Tri referenced() {
        return child.referenced();
    }

    @Override
    public MemberSet members() {
        return child.members();
    }

    @Override
    public Tri newable() {
        return child.newable();
    }

    @Override
    public EnumSet<TypeDomain> domains() {
        return child.domains();
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
