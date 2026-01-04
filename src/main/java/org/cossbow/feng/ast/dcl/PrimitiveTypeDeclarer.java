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
    public boolean equals(Object other) {
        if (other instanceof PrimitiveTypeDeclarer ptd) {
            return primitive == ptd.primitive;
        }
        return false;
    }
}
