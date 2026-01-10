package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;

public class ThisTypeDeclarer extends TypeDeclarer {
    public ThisTypeDeclarer(Position pos) {
        super(pos);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ThisTypeDeclarer;
    }

}
