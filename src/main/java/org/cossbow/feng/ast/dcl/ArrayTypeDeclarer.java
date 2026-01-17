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
    private boolean literal;

    public ArrayTypeDeclarer(Position pos,
                             TypeDeclarer element,
                             Optional<Expression> length,
                             Optional<Refer> refer,
                             boolean literal) {
        super(pos);
        this.element = element;
        this.length = length;
        this.refer = refer;
        this.literal = literal;
    }

    public ArrayTypeDeclarer(Position pos,
                             TypeDeclarer element,
                             Optional<Expression> length,
                             Optional<Refer> refer) {
        this(pos, element, length, refer, false);
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

    public boolean literal() {
        return literal;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArrayTypeDeclarer t))
            return false;
        return element.equals(t.element) &&
                length.equals(t.length) &&
                refer.equals(t.refer);
    }

}
