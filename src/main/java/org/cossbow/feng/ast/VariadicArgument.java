package org.cossbow.feng.ast;

import org.cossbow.feng.ast.expr.Expression;

import java.util.List;

public class VariadicArgument {
    private final List<Expression> arguments;

    public VariadicArgument(List<Expression> arguments) {
        this.arguments = arguments;
    }

    public List<Expression> arguments() {
        return arguments;
    }
}
