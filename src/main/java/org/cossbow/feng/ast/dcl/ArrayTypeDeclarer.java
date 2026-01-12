package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.Objects;

public class ArrayTypeDeclarer extends TypeDeclarer {
    private TypeDeclarer element;
    private Optional<Expression> length;
    private Optional<Reference> reference;

    public ArrayTypeDeclarer(Position pos,
                             TypeDeclarer element,
                             Optional<Expression> length,
                             Optional<Reference> reference) {
        super(pos);
        this.element = element;
        this.length = length;
        this.reference = reference;
    }

    public TypeDeclarer element() {
        return element;
    }

    public Optional<Expression> length() {
        return length;
    }

    public Optional<Reference> reference() {
        return reference;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArrayTypeDeclarer td)) return false;
        return Objects.equals(element, td.element) &&
                Objects.equals(length, td.length) &&
                Objects.equals(reference, td.reference);
    }

}
