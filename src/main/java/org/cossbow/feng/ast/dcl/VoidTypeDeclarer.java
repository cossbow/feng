package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;

/**
 * The internal type of the compiler cannot be defined syntactically.
 * <p>
 * When a function or method has no return value, it is equivalent
 * to having a return type of void.
 */
public class VoidTypeDeclarer extends TypeDeclarer {
    public VoidTypeDeclarer(Position pos) {
        super(pos);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VoidTypeDeclarer;
    }

    @Override
    public int hashCode() {
        return VoidTypeDeclarer.class.hashCode();
    }

    @Override
    public boolean isVoid() {
        return true;
    }

    //

    @Override
    public String toString() {
        return "<void>";
    }
}
