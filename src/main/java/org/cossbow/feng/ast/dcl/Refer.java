package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

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
}
