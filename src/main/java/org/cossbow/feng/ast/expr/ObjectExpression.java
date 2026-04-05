package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Optional;

import java.util.stream.Collectors;

public class ObjectExpression extends PrimaryExpression {
    private final IdentifierMap<Expression> entries;
    private final Optional<DerivedTypeDeclarer> type;

    public ObjectExpression(Position pos,
                            IdentifierMap<Expression> entries,
                            Optional<DerivedTypeDeclarer> type) {
        super(pos);
        this.entries = entries;
        this.type = type;
    }

    public ObjectExpression(Position pos,
                            IdentifierMap<Expression> entries) {
        this(pos, entries, Optional.empty());
    }

    public ObjectExpression(Position pos) {
        this(pos, new IdentifierMap<>(), Optional.empty());
    }

    public IdentifierMap<Expression> entries() {
        return entries;
    }

    public Optional<DerivedTypeDeclarer> type() {
        return type;
    }

    public DerivedTypeDeclarer dtd() {
        if (type.has()) return type.get();
        var t = resultType.must();
        if (t instanceof DerivedTypeDeclarer dtd)
            return dtd;
        throw new ErrorUtil.UnreachableException();
    }

    @Override
    public boolean unbound() {
        return true;
    }


    //

    @Override
    public String toString() {
        return entries.nodes().stream()
                .map(n -> n.key() + "=" + n.value())
                .collect(Collectors.joining(
                        ", ", "{", "}"));
    }
}
