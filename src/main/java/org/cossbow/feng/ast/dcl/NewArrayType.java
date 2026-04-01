package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class NewArrayType extends NewType {
    private TypeDeclarer element;
    private Expression length;

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

    public void element(TypeDeclarer element) {
        this.element = element;
    }

    public Expression length() {
        return length;
    }

    public void length(Expression length) {
        this.length = length;
    }


    //

    @Override
    public String toString() {
        return "[" + length + "]" + element;
    }
}
