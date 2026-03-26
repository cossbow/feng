package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.ObjectTypeDeclarer;
import org.cossbow.feng.util.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ObjectExpression extends PrimaryExpression {
    private final IdentifierTable<Expression> entries;
    private final Optional<DerivedTypeDeclarer> type;

    public ObjectExpression(Position pos,
                            IdentifierTable<Expression> entries,
                            Optional<DerivedTypeDeclarer> type) {
        super(pos);
        this.entries = entries;
        this.type = type;
    }

    public ObjectExpression(Position pos,
                            IdentifierTable<Expression> entries) {
        this(pos, entries, Optional.empty());
    }

    public ObjectExpression(Position pos) {
        this(pos, new IdentifierTable<>(), Optional.empty());
    }

    public IdentifierTable<Expression> entries() {
        return entries;
    }

    public Optional<DerivedTypeDeclarer> type() {
        return type;
    }

    public DerivedTypeDeclarer dtd() {
        if (type.has()) return type.get();
        var t = (ObjectTypeDeclarer) resultType.must();
        return t.lt.must();
    }

    @Override
    public boolean unbound() {
        return true;
    }

    @Deprecated
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
