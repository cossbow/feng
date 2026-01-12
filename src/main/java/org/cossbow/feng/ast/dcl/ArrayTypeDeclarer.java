package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.Objects;

public class ArrayTypeDeclarer extends TypeDeclarer
        implements Referable {
    private TypeDeclarer element;
    private Optional<Expression> length;
    private Optional<Refer> refer;

    public ArrayTypeDeclarer(Position pos,
                             TypeDeclarer element,
                             Optional<Expression> length,
                             Optional<Refer> refer) {
        super(pos);
        this.element = element;
        this.length = length;
        this.refer = refer;
    }

    public TypeDeclarer element() {
        return element;
    }

    public Optional<Expression> length() {
        return length;
    }

    public Optional<Refer> refer() {
        return refer;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArrayTypeDeclarer td)) return false;
        return Objects.equals(element, td.element) &&
                Objects.equals(length, td.length) &&
                Objects.equals(refer, td.refer);
    }

}
