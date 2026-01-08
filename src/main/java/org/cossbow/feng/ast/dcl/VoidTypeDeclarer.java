package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;

public class VoidTypeDeclarer extends TypeDeclarer {
    public VoidTypeDeclarer(Position pos) {
        super(pos);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VoidTypeDeclarer;
    }
}
