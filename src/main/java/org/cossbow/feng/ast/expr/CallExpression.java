package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.stmt.CallStatement;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.List;
import java.util.stream.Collectors;

public class CallExpression extends PrimaryExpression {
    private PrimaryExpression callee;
    private List<Expression> arguments;
    private final Optional<Prototype> prototype;

    public CallExpression(Position pos,
                          PrimaryExpression callee,
                          List<Expression> arguments,
                          Optional<Prototype> prototype) {
        super(pos);
        this.callee = callee;
        this.arguments = arguments;
        this.prototype = prototype;
    }

    public CallExpression(Position pos,
                          PrimaryExpression callee,
                          List<Expression> arguments) {
        this(pos, callee, arguments, Optional.empty());
    }

    public PrimaryExpression callee() {
        return callee;
    }

    public List<Expression> arguments() {
        return arguments;
    }

    public Optional<Prototype> prototype() {
        return prototype;
    }

    //


    private final Lazy<CallStatement> stmt = Lazy.nil();

    public Lazy<CallStatement> stmt() {
        return stmt;
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
