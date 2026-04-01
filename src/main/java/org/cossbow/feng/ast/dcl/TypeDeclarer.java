package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Optional;

abstract
public class TypeDeclarer extends Entity {
    public TypeDeclarer(Position pos) {
        super(pos);
    }

    public boolean baseTypeSame(TypeDeclarer td) {
        return true;
    }

    abstract public boolean equals(Object obj);

    abstract public int hashCode();

    public Optional<Refer> maybeRefer() {
        if (this instanceof Referable r)
            return r.refer();
        return Optional.empty();
    }

    public Optional<TypeDeclarer> derefer() {
        return Optional.empty();
    }

    public boolean referKind(ReferKind kind) {
        return this instanceof Referable r && r.isKind(kind);
    }

    private Boolean mappable;
    private Long size;

    public boolean mappable() {
        return mappable;
    }

    public void mappable(boolean mappable) {
        this.mappable = mappable;
    }

    public void size(long size) {
        this.size = size;
    }

    public Long size() {
        return size;
    }

    //

    public boolean isNil() {
        return false;
    }

    public boolean isBool() {
        return false;
    }

    public boolean isInteger() {
        return false;
    }

    public boolean isVoid() {
        return false;
    }

    public boolean required() {
        return maybeRefer().match(Refer::required);
    }

    public boolean hasTemplate() {
        return false;
    }
}
