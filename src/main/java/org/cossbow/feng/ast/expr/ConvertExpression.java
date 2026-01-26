package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Primitive;

public class ConvertExpression extends PrimaryExpression {
    private final Primitive primitive;
    private final Expression operand;

    public ConvertExpression(Position pos,
                             Primitive primitive,
                             Expression operand) {
        super(pos);
        this.primitive = primitive;
        this.operand = operand;
    }

    public Primitive primitive() {
        return primitive;
    }

    public Expression operand() {
        return operand;
    }

}
