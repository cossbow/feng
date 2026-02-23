package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.ArrayTypeDeclarer;

public class ArrayLenExpression extends PrimaryExpression {
    private final PrimaryExpression subject;
    private final ArrayTypeDeclarer type;

    public ArrayLenExpression(Position pos,
                              PrimaryExpression subject,
                              ArrayTypeDeclarer type) {
        super(pos);
        this.subject = subject;
        this.type = type;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public ArrayTypeDeclarer type() {
        return type;
    }

    //

    @Override
    public String toString() {
        return subject + "." + type.LengthField.name();
    }
}
