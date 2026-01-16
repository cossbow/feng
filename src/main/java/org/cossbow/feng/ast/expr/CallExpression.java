package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

import java.util.List;

public class CallExpression extends PrimaryExpression {
    private PrimaryExpression callee;
    private List<Expression> arguments;

    public CallExpression(Position pos,
                          PrimaryExpression callee,
                          List<Expression> arguments) {
        super(pos);
        this.callee = callee;
        this.arguments = arguments;
    }

    public PrimaryExpression callee() {
        return callee;
    }

    public List<Expression> arguments() {
        return arguments;
    }

}
