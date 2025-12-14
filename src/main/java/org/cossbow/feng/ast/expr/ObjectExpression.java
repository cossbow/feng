package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.IdentifierTable;

public class ObjectExpression extends PrimaryExpression {
    private final IdentifierTable<Expression> entries;

    public ObjectExpression(Position pos,
                            IdentifierTable<Expression> entries) {
        super(pos);
        this.entries = entries;
    }

    public IdentifierTable<Expression> entries() {
        return entries;
    }

}
