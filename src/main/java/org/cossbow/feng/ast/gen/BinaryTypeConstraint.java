package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Position;

public class BinaryTypeConstraint extends TypeConstraint {
    private TypeOperator operator;
    private TypeConstraint left, right;

    public BinaryTypeConstraint(Position pos,
                                TypeOperator operator,
                                TypeConstraint left,
                                TypeConstraint right) {
        super(pos);
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    public TypeOperator operator() {
        return operator;
    }

    public TypeConstraint left() {
        return left;
    }

    public TypeConstraint right() {
        return right;
    }
}
