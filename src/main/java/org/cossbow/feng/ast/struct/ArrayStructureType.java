package org.cossbow.feng.ast.struct;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class ArrayStructureType extends StructureType {
    private StructureType elementType;
    private Optional<Expression> length;

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
