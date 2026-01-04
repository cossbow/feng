package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

import java.util.List;
import java.util.stream.Collectors;

public class PairsExpression extends PrimaryExpression {
    private List<Pair> pairs;

    public PairsExpression(Position pos,
                           List<Pair> pairs) {
        super(pos);
        this.pairs = pairs;
    }

    public List<Pair> pairs() {
        return pairs;
    }

    public record Pair(Expression key, Expression value) {
    }

    //

    @Override
    public String toString() {
        return pairs.stream()
                .map(n -> n.key() + ": " + n.value())
                .collect(Collectors.joining(
                        ", ", "{", "}"));
    }
}
