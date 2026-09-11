package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.type.TypeConstraint;
import org.cossbow.feng.ast.type.TypeView;
import org.cossbow.feng.util.Optional;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generic type variable defined in functions and types,
 * should be replaced by {@link TypeArguments}.
 */
public class TypeParameter extends Entity {
    private Identifier name;
    private Optional<TypeConstraint> constraint;
    private boolean initable;

    public TypeParameter(Position pos,
                         Identifier name,
                         Optional<TypeConstraint> constraint,
                         boolean initable) {
        super(pos);
        this.name = name;
        this.constraint = constraint;
        this.initable = initable;
    }

    public Identifier name() {
        return name;
    }

    public Optional<TypeConstraint> constraint() {
        return constraint;
    }

    public void constraint(TypeConstraint c) {
        this.constraint = Optional.of(c);
    }

    public boolean initable() {
        return initable;
    }

    public boolean match(TypeDeclarer td) {
        if (constraint.none()) return true;
        return constraint.get().contains(td);
    }

    //

    private TypeView view;

    public Optional<TypeView> view() {
        if (constraint.none()) return Optional.empty();
        if (view == null) {
            view = constraint.get().view();
        }
        return Optional.of(view);
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
