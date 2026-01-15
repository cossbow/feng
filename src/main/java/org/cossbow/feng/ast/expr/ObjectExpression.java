package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.IdentifierTable;

public class ObjectExpression extends PrimaryExpression {
    private IdentifierTable<Expression> entries;

    public ObjectExpression(Position pos,
                            IdentifierTable<Expression> entries) {
        super(pos);
        this.entries = entries;
    }

    public IdentifierTable<Expression> entries() {
        return entries;
    }

    @Override
    public boolean isFinal() {
        return entries.values().stream().allMatch(Expression::isFinal);
    }
}
