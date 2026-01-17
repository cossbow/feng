package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;

public class PrimitiveTypeDeclarer extends TypeDeclarer {
    private Primitive primitive;

    public PrimitiveTypeDeclarer(Position pos,
                                 Primitive primitive) {
        super(pos);
        this.primitive = primitive;
    }

    public Primitive primitive() {
        return primitive;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PrimitiveTypeDeclarer t))
            return false;

        return primitive == t.primitive;
    }

    @Override
    public String toString() {
        return primitive.code;
    }
}
