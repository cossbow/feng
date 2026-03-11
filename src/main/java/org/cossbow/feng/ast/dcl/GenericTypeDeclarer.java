package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericType;
import org.cossbow.feng.ast.gen.TypeParameter;
import org.cossbow.feng.util.Lazy;

public class GenericTypeDeclarer extends TypeDeclarer {
    private final GenericType type;

    public GenericTypeDeclarer(
            Position pos, GenericType type) {
        super(pos);
        this.type = type;
    }

    public GenericType type() {
        return type;
    }

    public TypeParameter param() {
        return type.param();
    }

    public boolean hasTemplate() {
        return true;
    }

    //

    public boolean equals(Object o) {
        return o instanceof GenericTypeDeclarer t
                && type.equals(t.type);
    }

    public int hashCode() {
        return type.hashCode();
    }

    //

    @Override
    public String toString() {
        return type.toString();
    }
}
