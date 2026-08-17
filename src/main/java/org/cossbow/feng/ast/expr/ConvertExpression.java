package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.ast.gen.GenericMap;

/**
 * Only primitive-types support conversion expressions:
 * <p>
 * {@code var a = uint(b);},
 * {@code var a = float32(b);},
 * etc.
 */
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

    @Override
    public ConvertExpression mirror() {
        return new ConvertExpression(pos(), primitive, operand.mirror());
    }

    @Override
    public ConvertExpression mono(GenericMap gm) {
        return monoCopy(new ConvertExpression(pos(), primitive, operand.mono(gm)), gm);
    }

    //

    @Override
    public String toString() {
        return primitive + "(" + operand + ")";
    }
}
