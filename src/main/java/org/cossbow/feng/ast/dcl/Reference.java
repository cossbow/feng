package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

public class Reference extends Entity {
    private final ReferenceType type;
    private final boolean required;

    public Reference(Position pos,
                     ReferenceType type,
                     boolean required) {
        super(pos);
        this.type = type;
        this.required = required;
    }

    public ReferenceType type() {
        return type;
    }

    public boolean required() {
        return required;
    }
}
