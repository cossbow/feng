package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.FuncTypeDeclarer;
import org.cossbow.feng.ast.dcl.PrimitiveTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.Objects;

public class DomainTypeConstraint extends TypeConstraint {
    private final TypeDomain domain;

    public DomainTypeConstraint(Position pos, TypeDomain domain) {
        super(pos);
        this.domain = domain;
    }

    public TypeDomain domain() {
        return domain;
    }

    @Override
    public boolean contains(TypeDeclarer t) {
        return domainOf(t) == domain;
    }

    private static TypeDomain domainOf(TypeDeclarer t) {
        if (t instanceof PrimitiveTypeDeclarer p) {
            return p.primitive().type().domain();
        }
        if (t instanceof DerivedTypeDeclarer d) {
            return d.def().domain();
        }
        if (t instanceof FuncTypeDeclarer) {
            return TypeDomain.FUNC;
        }
        return null;
    }

    @Override
    public TypeView view() {
        return TypeView.domain(domain);
    }

    //

    @Override
    public final boolean equals(Object o) {
        return o instanceof DomainTypeConstraint c
                && domain == c.domain;

    }

    @Override
    public int hashCode() {
        return domain.hashCode();
    }

    //
    @Override
    public String toString() {
        return String.valueOf(domain);
    }
}
