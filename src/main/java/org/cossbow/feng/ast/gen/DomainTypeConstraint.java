package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDomain;

public class DomainTypeConstraint extends TypeConstraint {
    private TypeDomain domain;

    public DomainTypeConstraint(Position pos, TypeDomain domain) {
        super(pos);
        this.domain = domain;
    }

    public TypeDomain domain() {
        return domain;
    }
}
