package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class ArrayTypeDeclarer extends TypeDeclarer {
    private final TypeDeclarer elementType;
    private final Optional<Expression> length;

    public ArrayTypeDeclarer(Position pos,
                             TypeDeclarer elementType,
                             Optional<Expression> length) {
        super(pos);
        this.elementType = elementType;
        this.length = length;
    }

    public TypeDeclarer elementType() {
        return elementType;
    }

    public Optional<Expression> length() {
        return length;
    }
}
