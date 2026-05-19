package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.ArrayTypeDeclarer;

/**
 * The internal expressions of a compiler cannot be syntactically defined.
 * <p>
 * Created to facilitate the implementation of array length.
 */
public class ArrayLenExpression extends PrimaryExpression {
    private final PrimaryExpression subject;

    public ArrayLenExpression(Position pos,
                              PrimaryExpression subject) {
        super(pos);
        this.subject = subject;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    //

    @Override
    public String toString() {
        return subject + "." + ArrayTypeDeclarer.FieldLength.name();
    }
}
