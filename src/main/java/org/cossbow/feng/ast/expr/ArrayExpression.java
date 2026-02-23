package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

import java.util.List;

public class ArrayExpression extends PrimaryExpression {
    private List<Expression> elements;

    public ArrayExpression(Position pos,
                           List<Expression> elements) {
        super(pos);
        this.elements = elements;
    }

    public List<Expression> elements() {
        return elements;
    }

    public int size() {
        return elements.size();
    }

    @Override
    public boolean isFinal() {
        return elements.stream().allMatch(Expression::isFinal);
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
