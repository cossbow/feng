package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class ArrayTypeDeclarer extends TypeDeclarer {
    private final TypeDeclarer element;
    private final Optional<Expression> length;
    private final boolean immutable;

    public ArrayTypeDeclarer(Position pos,
                             TypeDeclarer element,
                             Optional<Expression> length,
                             boolean immutable) {
        super(pos);
        this.element = element;
        this.length = length;
        this.immutable = immutable;
    }

    public TypeDeclarer element() {
        return element;
    }

    public Optional<Expression> length() {
        return length;
    }

    public boolean immutable() {
        return immutable;
    }
}
