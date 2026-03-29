package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.proc.Prototype;

public class AnonFuncTypeDeclarer extends FuncTypeDeclarer {
    private final Prototype prototype;

    public AnonFuncTypeDeclarer(Position pos,
                                boolean required,
                                Prototype prototype) {
        super(pos, required);
        this.prototype = prototype;
    }

    public Prototype prototype() {
        return prototype;
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AnonFuncTypeDeclarer t))
            return false;

        return required() == t.required() &&
                prototype.equals(t.prototype);
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(required()) * 31
                + prototype.hashCode();
    }

    //
    @Override
    public String toString() {
        if (required()) return "!" + prototype;
        return prototype.toString();
    }
}
