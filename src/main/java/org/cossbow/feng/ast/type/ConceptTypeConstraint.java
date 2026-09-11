package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

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
        return concept.expr().contains(t);
    }

    @Override
    public TypeView view() {
        return concept.expr().view();
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
