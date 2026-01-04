package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;

public class TypeParameter extends Entity {
    private Identifier name;
    private Optional<TypeConstraint> constraint;

    public TypeParameter(Position pos,
                         Identifier name,
                         Optional<TypeConstraint> constraint) {
        super(pos);
        this.name = name;
        this.constraint = constraint;
    }

    public Identifier name() {
        return name;
    }

    public Optional<TypeConstraint> constraint() {
        return constraint;
    }
}
