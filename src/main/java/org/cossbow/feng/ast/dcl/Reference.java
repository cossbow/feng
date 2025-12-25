package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

public class Reference extends Entity {
    private final ReferenceType type;
    private final boolean required;
    private final boolean immutable;

    public Reference(Position pos,
                     ReferenceType type,
                     boolean required, boolean immutable) {
        super(pos);
        this.type = type;
        this.required = required;
        this.immutable = immutable;
    }

    public ReferenceType type() {
        return type;
    }

    public boolean required() {
        return required;
    }

    public boolean immutable() {
        return immutable;
    }
}
