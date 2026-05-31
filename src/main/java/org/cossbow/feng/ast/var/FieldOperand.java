package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.expr.MemberOfExpression;
import org.cossbow.feng.ast.expr.PrimaryExpression;
import org.cossbow.feng.ast.gen.TypeArguments;

/**
 * Field operand: used for modify the fields of instance.
 * <p>
 * Requires the type of the instance have modifable field,
 * and the instance must be modifable.
 * <p>
 * Example:
 * <p>
 * {@code a.id = i;}
 * <p>
 * {@code a.b.value = v;}
 */
public class FieldOperand extends Operand {
    /**
     * The instance of the field belong to.
     */
    private final PrimaryExpression subject;
    /**
     * The field name
     */
    private final Identifier field;

    public FieldOperand(Position pos,
                        PrimaryExpression subject,
                        Identifier field) {
        super(pos);
        this.subject = subject;
        this.field = field;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public Identifier field() {
        return field;
    }

    @Override
    public Expression rhs() {
        return new MemberOfExpression(pos(), subject, field, TypeArguments.EMPTY);
    }

    //


    @Override
    public String toString() {
        return subject + "." + field;
    }
}
