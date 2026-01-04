package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.List;

public class SwitchTuple extends Tuple {
    private Expression value;
    private List<Rule> rules;
    private Tuple defaultRule;

    public SwitchTuple(Position pos,
                       Expression value,
                       List<Rule> rules,
                       Tuple defaultRule) {
        super(pos);
        this.value = value;
        this.rules = rules;
        this.defaultRule = defaultRule;
    }

    public Expression value() {
        return value;
    }

    public List<Rule> rules() {
        return rules;
    }

    public Tuple defaultRule() {
        return defaultRule;
    }

    @Override
    public int size() {
        return defaultRule.size();
    }

    //

    public record Rule(List<Expression> constants, Tuple tuple) {
    }
}
