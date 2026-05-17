package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.stmt.CallStatement;
import org.cossbow.feng.util.Lazy;
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
     * The atgument passed to the procedure
     */
    private final List<Expression> arguments;
    /**
     * The call expression created during the analysis phase
     * will be set as the actual procedure prototype
     */
    private final Optional<Prototype> prototype;

    private CallExpression(Position pos,
                           PrimaryExpression callee,
                           List<Expression> arguments,
                           Optional<Prototype> prototype) {
        super(pos);
        this.callee = callee;
        this.arguments = arguments;
        this.prototype = prototype;
    }

    /**
     * Created in the analysis stage
     */
    public CallExpression(Position pos,
                          PrimaryExpression callee,
                          List<Expression> arguments,
                          Prototype prototype) {
        this(pos, callee, arguments, Optional.of(prototype));
    }

    /**
     * Created during the parsing stage
     */
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

    /**
     * The procedure return value is a temporary value, unbound
     */
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
