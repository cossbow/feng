package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.UniqueTable;

import java.util.List;

public class ObjectExpression extends PrimaryExpression {
    private final UniqueTable<Expression> entries;

    public ObjectExpression(Position pos,
                            UniqueTable<Expression> entries) {
        super(pos);
        this.entries = entries;
    }

    public UniqueTable<Expression> entries() {
        return entries;
    }

    public record Entry(Identifier key, Expression value) {
    }
}
