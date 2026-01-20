package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

import java.util.Objects;

public class Refer extends Entity {
    private ReferKind kind;
    private boolean required;
    private boolean immutable;

    public Refer(Position pos,
                 ReferKind kind,
                 boolean required,
                 boolean immutable) {
        super(pos);
        this.kind = kind;
        this.required = required;
        this.immutable = immutable;
    }

    public ReferKind kind() {
        return kind;
    }

    public boolean checkType(ReferKind t) {
        return kind == t;
    }

    public boolean required() {
        return required;
    }

    public boolean immutable() {
        return immutable;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof Refer r)) return false;

        return required == r.required &&
                immutable == r.immutable
                && kind == r.kind;
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + Boolean.hashCode(required);
        result = 31 * result + Boolean.hashCode(immutable);
        return result;
    }

    //


    @Override
    public String toString() {
        var s = new StringBuilder(4);
        s.append(kind.symbol);
        if (required) s.append('!');
        if (immutable) s.append('#');
        return s.toString();
    }
}
