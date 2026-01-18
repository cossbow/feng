package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;

public class ConvertorTypeDeclarer extends TypeDeclarer {
    private Primitive primitive;

    public ConvertorTypeDeclarer(Position pos, Primitive primitive) {
        super(pos);
        this.primitive = primitive;
    }

    public Primitive primitive() {
        return primitive;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ConvertorTypeDeclarer;
    }

}
