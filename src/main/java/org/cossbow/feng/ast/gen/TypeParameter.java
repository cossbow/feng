package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Optional;

import java.util.concurrent.atomic.AtomicInteger;

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

    //

    private final int id = IdGenerator.getAndIncrement();

    public int id() {
        return id;
    }

    private static final AtomicInteger IdGenerator = new AtomicInteger(1);

    //

    public boolean equals(Object o) {
        return o instanceof TypeParameter p
                && id == p.id;
    }

    public int hashCode() {
        return id;
    }

    //

    @Override
    public String toString() {
        return name.toString();
    }
}
