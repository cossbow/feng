package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.ArrayTypeDeclarer;
import org.cossbow.feng.util.Optional;

import java.util.List;

public class ArrayExpression extends PrimaryExpression {
    private final List<Expression> elements;
    private final Optional<ArrayTypeDeclarer> type;

    public ArrayExpression(Position pos,
                           List<Expression> elements,
                           Optional<ArrayTypeDeclarer> type) {
        super(pos);
        this.elements = elements;
        this.type = type;
    }

    public ArrayExpression(Position pos,
                           List<Expression> elements) {
        this(pos, elements, Optional.empty());
    }

    public List<Expression> elements() {
        return elements;
    }

    public Optional<ArrayTypeDeclarer> type() {
        return type;
    }

    public int size() {
        return elements.size();
    }

    @Override
    public boolean unbound() {
        return true;
    }

    //

    @Override
    public String toString() {
        return elements.toString();
    }
}
