package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.PrimaryExpression;
import org.cossbow.feng.util.Lazy;

import java.util.List;

public class IterableForStatement extends ForStatement {
    private List<Identifier> arguments;
    private PrimaryExpression iterable;

    public IterableForStatement(Position pos,
                                BlockStatement body,
                                List<Identifier> arguments,
                                PrimaryExpression iterable) {
        super(pos, body);
        this.arguments = arguments;
        this.iterable = iterable;
    }

    public List<Identifier> arguments() {
        return arguments;
    }

    public PrimaryExpression iterable() {
        return iterable;
    }

    public void iterable(PrimaryExpression iterable) {
        this.iterable = iterable;
    }

    //

    public final Lazy<ConditionalForStatement> replace = Lazy.nil();

}
