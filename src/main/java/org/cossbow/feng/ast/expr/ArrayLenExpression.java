package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.ArrayTypeDeclarer;

public class ArrayLenExpression extends PrimaryExpression {
    private final ArrayTypeDeclarer array;

    public ArrayLenExpression(Position pos,
                              ArrayTypeDeclarer array) {
        super(pos);
        this.array = array;
    }

    public ArrayTypeDeclarer array() {
        return array;
    }

    //

    @Override
    public String toString() {
        return array + "." + array.LengthField.name();
    }
}
