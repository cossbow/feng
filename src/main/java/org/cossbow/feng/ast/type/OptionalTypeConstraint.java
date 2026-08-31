package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.EnumSet;

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
        if (r.has() && !r.get().required()) return true;
        return t instanceof GenericTypeDeclarer gtd
                && containsByConstraint(gtd);
    }

    @Override
    public boolean include(TypeConstraint tc) {
        // 可空维度无上近似投影，剥壳 equals 除外保守 false
        return equals(normalize(tc));
    }

    @Override
    public Tri referenced() {
        // 可空引用：本身仍是引用
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
