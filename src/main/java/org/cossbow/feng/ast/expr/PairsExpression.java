package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

import java.util.List;

public class PairsExpression extends PrimaryExpression {
    private final List<Pair> pairs;

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
}
