package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class NewArrayType extends NewType {
    private TypeDeclarer element;
    private Expression length;
    private boolean immutable;

    public NewArrayType(Position pos,
                        TypeDeclarer element,
                        Expression length,
                        boolean immutable) {
        super(pos);
        this.element = element;
        this.length = length;
        this.immutable = immutable;
    }

    public TypeDeclarer element() {
        return element;
    }

    public Expression length() {
        return length;
    }

    public boolean immutable() {
        return immutable;
    }
}
