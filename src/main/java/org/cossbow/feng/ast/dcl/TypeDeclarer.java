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

    public TypeDeclarer unmap() {
        if (this instanceof MemTypeDeclarer mtd) {
            return mtd.mapped().must();
        }
        return this;
    }
}
