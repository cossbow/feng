package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.EnumSet;

public class ConceptTypeConstraint extends TypeConstraint {
    private final Concept concept;

    public ConceptTypeConstraint(
            Position pos, Concept concept) {
        super(pos);
        this.concept = concept;
    }

    public Concept concept() {
        return concept;
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        if (t instanceof GenericTypeDeclarer gtd) {
            // 类型变量分支（§3.4）：委托 include 做集合包含
            return containsByConstraint(gtd);
        }
        return concept.expr().contains(t);
    }

    @Override
    public boolean include(TypeConstraint tc) {
        return concept.expr().include(tc);
    }

    @Override
    public Tri referenced() {
        return concept.expr().referenced();
    }

    @Override
    public MemberSet members() {
        return concept.expr().members();
    }

    @Override
    public Tri newable() {
        return concept.expr().newable();
    }

    @Override
    public EnumSet<TypeDomain> domains() {
        return concept.expr().domains();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ConceptTypeConstraint c
                && concept.equals(c.concept);
    }

    @Override
    public int hashCode() {
        return concept.hashCode();
    }

    @Override
    public String toString() {
        return concept.expr().toString();
    }
}
