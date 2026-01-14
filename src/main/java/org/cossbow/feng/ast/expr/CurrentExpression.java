package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;

public class CurrentExpression extends PrimaryExpression {
    private final Symbol className;
    private final Identifier method;
    private boolean isSelf;

    public CurrentExpression(Position pos,
                             Symbol className,
                             Identifier method,
                             boolean isSelf) {
        super(pos);
        this.className = className;
        this.method = method;
        this.isSelf = isSelf;
    }

    public Symbol type() {
        return className;
    }

    public Identifier method() {
        return method;
    }

    public boolean isSelf() {
        return isSelf;
    }

    public String name() {
        return isSelf ? "this" : "super";
    }
}
