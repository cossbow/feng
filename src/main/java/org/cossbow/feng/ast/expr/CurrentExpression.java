package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;

public class CurrentExpression extends PrimaryExpression {
    private final Symbol className;
    private boolean isSelf;

    public CurrentExpression(Position pos,
                             Identifier className,
                             boolean isSelf) {
        super(pos);
        this.className = new Symbol(className.pos(), className);
        this.isSelf = isSelf;
    }

    public Symbol type() {
        return className;
    }

    public boolean isSelf() {
        return isSelf;
    }

}
