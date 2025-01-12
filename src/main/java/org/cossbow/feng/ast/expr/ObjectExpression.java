package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

import java.util.List;

public class ObjectExpression extends PrimaryExpression {
    private final List<Entry> entries;

    public ObjectExpression(Position pos,
                            List<Entry> entries) {
        super(pos);
        this.entries = entries;
    }

    public List<Entry> entries() {
        return entries;
    }

    public record Entry(Identifier key, Expression value) {
    }
}
