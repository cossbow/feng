package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.Objects;

public class ArrayTypeDeclarer extends TypeDeclarer {
    private TypeDeclarer element;
    private Optional<Expression> length;
    private boolean immutable;

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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArrayTypeDeclarer atd)) return false;
        return immutable == atd.immutable &&
                Objects.equals(element, atd.element) &&
                Objects.equals(length, atd.length);
    }

}
