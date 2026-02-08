package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.util.Stack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ObjectExpression extends PrimaryExpression {
    private final IdentifierTable<Expression> entries;

    public ObjectExpression(Position pos,
                            IdentifierTable<Expression> entries) {
        super(pos);
        this.entries = entries;
    }

    public ObjectExpression(Position pos) {
        this(pos, new IdentifierTable<>());
    }

    public IdentifierTable<Expression> entries() {
        return entries;
    }

    @Override
    public boolean isFinal() {
        return entries.values().stream().allMatch(Expression::isFinal);
    }

    @Override
    public boolean unbound() {
        return true;
    }

    public final List<List<Field>> initStack = new ArrayList<>();

    //

    @Override
    public String toString() {
        return entries.nodes().stream()
                .map(n -> n.key() + "=" + n.value())
                .collect(Collectors.joining(
                        ", ", "{", "}"));
    }
}
