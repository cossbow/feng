package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.EnumSet;

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
        if (t.maybeRefer().has()) return true;
        return t instanceof GenericTypeDeclarer gtd
                && containsByConstraint(gtd);
    }

    @Override
    public boolean include(TypeConstraint tc) {
        var x = normalize(tc);
        if (equals(x)) return true;
        // referenced() == YES ⟹ 集合中每个元素都是引用 → 全部命中 *any
        return x.referenced() == Tri.YES;
    }

    @Override
    public Tri referenced() {
        return Tri.YES;
    }

    @Override
    public MemberSet members() {
        return MemberSet.empty();
    }

    @Override
    public Tri newable() {
        return Tri.BOTH;
    }

    @Override
    public EnumSet<TypeDomain> domains() {
        return allDomains();
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
