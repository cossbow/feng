package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;

/**
 * 临时，不在AST上
 */
public class VoidTypeDeclarer extends TypeDeclarer {
    public VoidTypeDeclarer(Position pos) {
        super(pos);
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return VoidTypeDeclarer.class.hashCode();
    }
}
