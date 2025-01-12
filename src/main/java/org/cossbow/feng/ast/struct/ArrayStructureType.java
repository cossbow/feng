package org.cossbow.feng.ast.struct;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.Optional;

public class ArrayStructureType extends StructureType {
    private final StructureType elementType;
    private final Optional<Expression> length;

    public ArrayStructureType(Position pos,
                              StructureType elementType,
                              Optional<Expression> length) {
        super(pos);
        this.elementType = elementType;
        this.length = length;
    }

    public StructureType elementType() {
        return elementType;
    }

    public Optional<Expression> length() {
        return length;
    }
}
