package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;

/**
 * Type Checking and Conversion:
 * <p>
 * Checking the type of {@code  subject} is matched {@code type}.
 * <p>
 * Only classes (non-final classes) and interfaces are supported.
 * <p>
 * Polymorphism and abstraction should not only support covariance,
 * but also support dynamic non-covariant conversions. Example:
 * <p>
 * {@code var a *Animal = new(Cat); var c = a?(*Cat);}
 * <p>
 * If real type not match {@code type}, nil is returned:
 * <p>
 * {@code var a *Animal = new(Dog); var c = a?(*Cat);}
 * {@code c} will be set to nil.
 */
public class IsExpression extends PrimaryExpression {
    private final PrimaryExpression subject;
    private final DerivedTypeDeclarer type;

    public IsExpression(Position pos,
                        PrimaryExpression subject,
                        DerivedTypeDeclarer type) {
        super(pos);
        this.subject = subject;
        this.type = type;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public DerivedTypeDeclarer type() {
        return type;
    }

    public boolean unbound() {
        return subject.unbound();
    }

    //

    /**
     * Requires dynamic type checking.
     */
    private boolean needCheck;

    public boolean needCheck() {
        return needCheck;
    }

    public void needCheck(boolean needCheck) {
        this.needCheck = needCheck;
    }

    //

    @Override
    public String toString() {
        return subject + "?(" + type + ')';
    }
}
