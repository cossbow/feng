package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

/**
 * Reference attribute of {@link TypeDeclarer}.
 * <p>
 * {@code var v *int;} is normal reference
 * <p>
 * {@code var v *!int;} is a required reference
 * <p>
 * {@code var v *#int;} is a unmodifiable reference
 * <p>
 * {@code var v *!#int;} is a required and unmodifiable reference
 */
public class Refer extends Entity {
    private final ReferKind kind;
    /**
     * required: it can't be nil if true.
     */
    private final boolean required;
    /**
     * unmodifiable: can't modify the instance
     * which be reference if true.
     */
    private final boolean unmodifiable;

    public Refer(Position pos,
                 ReferKind kind,
                 boolean required,
                 boolean unmodifiable) {
        super(pos);
        this.kind = kind;
        this.required = required;
        this.unmodifiable = unmodifiable;
    }

    public ReferKind kind() {
        return kind;
    }

    public boolean isKind(ReferKind t) {
        return kind == t;
    }

    public boolean required() {
        return required;
    }

    public boolean unmodifiable() {
        return unmodifiable;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Refer r)) return false;

        return required == r.required &&
                unmodifiable == r.unmodifiable
                && kind == r.kind;
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + Boolean.hashCode(required);
        result = 31 * result + Boolean.hashCode(unmodifiable);
        return result;
    }

    //


    @Override
    public String toString() {
        var s = new StringBuilder(4);
        s.append(kind.symbol);
        if (required) s.append('!');
        if (unmodifiable) s.append('#');
        return s.toString();
    }
}
