package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Position;

public class BinaryTypeExpression extends TypeExpression {
    private final TypeOperator operator;
    private final TypeExpression left, right;

    public BinaryTypeExpression(Position pos,
                                TypeOperator operator,
                                TypeExpression left,
                                TypeExpression right) {
        super(pos);
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    public TypeOperator operator() {
        return operator;
    }

    public TypeExpression left() {
        return left;
    }

    public TypeExpression right() {
        return right;
    }
}
