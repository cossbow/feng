package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Optional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Use to calling a procedure (a function or a method), example:
 * <p>
 * {@code max(a, b)}
 * {@code th.start()}
 */
public class CallExpression extends PrimaryExpression {
    /**
     * return type of {@code callee} is a procedure
     */
    private final PrimaryExpression callee;
    /**
     * The argument passed to the procedure
     */
    private final List<Expression> arguments;
    /**
     * Variadic argument need to expand
     */
    private final boolean variadic;
    /**
     * The call expression created during the analysis phase
     * will be set as the actual procedure prototype
     */
    private final Optional<Prototype> prototype;

    private CallExpression(Position pos,
                           PrimaryExpression callee,
                           List<Expression> arguments,
                           boolean variadic,
                           Optional<Prototype> prototype) {
        super(pos);
        this.callee = callee;
        this.arguments = arguments;
        this.variadic = variadic;
        this.prototype = prototype;
    }

    /**
     * Created in the analysis stage
     */
    public CallExpression(Position pos,
                          PrimaryExpression callee,
                          List<Expression> arguments,
                          Prototype prototype) {
        this(pos, callee, arguments, false,
                Optional.of(prototype));
    }

    /**
     * Created in the analysis stage, preserving variadic info
     */
    public CallExpression(Position pos,
                          PrimaryExpression callee,
                          List<Expression> arguments,
                          boolean variadic,
                          Prototype prototype) {
        this(pos, callee, arguments, variadic,
                Optional.of(prototype));
    }

    /**
     * Created during the parsing stage
     */
    public CallExpression(Position pos,
                          PrimaryExpression callee,
                          List<Expression> arguments,
                          boolean variadic) {
        this(pos, callee, arguments, variadic, Optional.empty());
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

    public boolean variadic() {
        return variadic;
    }

    //

    /**
     * The procedure return value is a temporary value, unbound
     */
    @Override
    public boolean unbound() {
        return true;
    }

    //

    /**
     * true: the call-expression use in expressions,
     * false: directly call as call-statement
     */
    private boolean asExpr;

    public boolean asExpr() {
        return asExpr;
    }

    public void asExpr(boolean asExpr) {
        this.asExpr = asExpr;
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
