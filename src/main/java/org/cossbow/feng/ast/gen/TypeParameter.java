package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.type.MemberSet;
import org.cossbow.feng.ast.type.Tri;
import org.cossbow.feng.ast.type.TypeConstraint;
import org.cossbow.feng.util.Optional;

import java.util.EnumSet;
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

    /**
     * 该类型参数在「引用性」维度上的投影。
     * 无约束 → {@link Tri#BOTH}（可值可引用）。
     */
    public Tri referenced() {
        if (constraint.none()) return Tri.BOTH;
        return constraint.get().referenced();
    }

    /**
     * 该类型参数在「成员集」维度上的下近似。
     * 无约束 → 空下近似。
     */
    public MemberSet members() {
        if (constraint.none()) return MemberSet.empty();
        return constraint.get().members();
    }

    /**
     * 该类型参数在「能否 new」维度上的投影。
     * 无约束 → {@link Tri#BOTH}（不确定）。
     */
    public Tri newable() {
        if (constraint.none()) return Tri.BOTH;
        return constraint.get().newable();
    }

    /**
     * 该类型参数在「域」维度上的投影。
     * 无约束 → 全部 8 域。
     */
    public EnumSet<TypeDomain> domains() {
        if (constraint.none()) return EnumSet.allOf(TypeDomain.class);
        return constraint.get().domains();
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
