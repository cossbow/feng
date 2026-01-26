package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Optional;

abstract
public class TypeDeclarer extends Entity {
    public TypeDeclarer(Position pos) {
        super(pos);
    }

    abstract public boolean equals(Object obj);

    abstract public int hashCode();

    public Optional<Refer> maybeRefer() {
        if (this instanceof Referable r)
            return r.refer();
        return Optional.empty();
    }

    public boolean referKind(ReferKind kind) {
        return this instanceof Referable r && r.isKind(kind);
    }

    private long unit;

    public long unit() {
        return unit;
    }

    public void unit(long unit) {
        this.unit = unit;
    }

    //

    public boolean isNil() {
        return false;
    }

    public boolean isBool() {
        return false;
    }

}
