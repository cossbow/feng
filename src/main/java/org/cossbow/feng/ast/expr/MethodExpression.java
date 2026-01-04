package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Method;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.TypeArguments;

public class MethodExpression extends PrimaryExpression {
    private PrimaryExpression subject;
    private Method method;
    private TypeArguments generic;

    public MethodExpression(Position pos,
                            PrimaryExpression subject,
                            Method method,
                            TypeArguments generic) {
        super(pos);
        this.subject = subject;
        this.method = method;
        this.generic = generic;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public Method method() {
        return method;
    }

    public TypeArguments generic() {
        return generic;
    }


    //

    @Override
    public String toString() {
        return subject + "." + method.name() + method.prototype();
    }
}
