package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

public class GenericType extends DefinedType {
    private final TypeParameter param;

    public GenericType(Position pos,
                       TypeParameter param) {
        super(pos);
        this.param = param;
    }

    public TypeParameter param() {
        return param;
    }

    public Identifier name() {
        return param.name();
    }

    public boolean equals(Object o) {
        return o instanceof GenericType t
                && param.equals(t.param);
    }

    public int hashCode() {
        return param.hashCode();
    }

    //

    @Override
    public String toString() {
        return param.toString();
    }
}
