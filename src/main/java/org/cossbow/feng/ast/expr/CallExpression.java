package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Lazy;

import java.util.List;
import java.util.stream.Collectors;

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

    //

    private final Lazy<Prototype> prototype = Lazy.nil();

    public Lazy<Prototype> prototype() {
        return prototype;
    }

    @Override
    public boolean unbound() {
        return true;
    }

    //

    @Override
    public String toString() {
        if (arguments.isEmpty())
            return callee + "()";
        return callee + arguments.stream().map(Object::toString)
                .collect(Collectors.joining(
                        ",", "(", ")"));
    }
}
