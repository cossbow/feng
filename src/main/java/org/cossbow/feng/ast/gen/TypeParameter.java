package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

import java.util.Optional;

public class TypeParameter extends Entity {
    private final Identifier name;
    private final Optional<TypeExpression> constraint;

    public TypeParameter(Position pos,
                         Identifier name,
                         Optional<TypeExpression> constraint) {
        super(pos);
        this.name = name;
        this.constraint = constraint;
    }

    public Identifier name() {
        return name;
    }

    public Optional<TypeExpression> constraint() {
        return constraint;
    }
}
