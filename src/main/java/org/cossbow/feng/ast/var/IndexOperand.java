package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.IndexOfExpression;
import org.cossbow.feng.ast.expr.PrimaryExpression;

/**
 * Index operand: used for modify the elements at index.
 * <p>
 *
 * <p>
 * Example: {@code a[0] = 1;}
 */
public class IndexOperand extends Operand {
    /**
     * The type of subject requires array or array-reference:
     * <p>
     * {@link org.cossbow.feng.ast.dcl.ArrayTypeDeclarer}
     * <p>
     * Classes which implements the macro 'index-set' can also
     * be enabled:
     * <p>
     * {@link org.cossbow.feng.ast.oop.ClassDefinition#indexOperator}
     * <p>
     * {@link org.cossbow.feng.ast.IndexOperator#set}
     */
    private final PrimaryExpression subject;
    /**
     * If type of subject is array or array-reference, the type
     * of index requires integer:
     * <p>
     * {@link org.cossbow.feng.ast.dcl.Primitive.Kind#INTEGER}
     * <p>
     * For the classes which implements the macro 'index-set',
     * the type of index is the parameter type.
     */
    private final Expression index;

    public IndexOperand(Position pos,
                        PrimaryExpression subject,
                        Expression index) {
        super(pos);
        this.subject = subject;
        this.index = index;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public Expression index() {
        return index;
    }

    @Override
    public Expression rhs() {
        return new IndexOfExpression(pos(), subject, index);
    }

    //

    @Override
    public String toString() {
        return subject + "[" + index + "]";
    }
}
