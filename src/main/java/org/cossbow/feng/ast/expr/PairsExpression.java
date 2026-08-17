package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Temporarily useless expression type.
 * <p>
 * {@code {1:a,2:b}}
 */
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

    @Override
    public PairsExpression mirror() {
        var l = new ArrayList<Pair>(pairs.size());
        for (var p : pairs) l.add(new Pair(p.key().mirror(), p.value().mirror()));
        return new PairsExpression(pos(), l);
    }

    @Override
    public PairsExpression mono(GenericMap gm) {
        var l = new ArrayList<Pair>(pairs.size());
        for (var p : pairs) l.add(new Pair(p.key().mono(gm), p.value().mono(gm)));
        return monoCopy(new PairsExpression(pos(), l), gm);
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
