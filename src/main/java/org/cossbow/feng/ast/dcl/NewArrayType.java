package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class NewArrayType extends NewType {
    private final TypeDeclarer element;
    private final Expression length;

    public NewArrayType(Position pos,
                        TypeDeclarer element,
                        Expression length) {
        super(pos);
        this.element = element;
        this.length = length;
    }

    public TypeDeclarer element() {
        return element;
    }

    public Expression length() {
        return length;
    }
}
