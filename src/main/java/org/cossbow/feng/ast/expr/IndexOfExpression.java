package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;

/**
 * Get value by index, for example: {@code arr[1]}
 * <p>
 * 1. Arrays are supported by default, and the type of
 * index is integer: {@code arr[1]}
 * <p>
 * 2. Enumerated types can obtain their values through
 * integer indexing: {@code State[1]}
 * <p>
 * 3. The class can support custom indexing operations,
 * and the index type is also customizable: {@code map["id"]}
 */
public class IndexOfExpression extends PrimaryExpression {
    private final PrimaryExpression subject;
    private final Expression index;

    public IndexOfExpression(Position pos,
                             PrimaryExpression subject,
                             Expression index) {
        super(pos);
        this.subject = subject;
        this.index = index;
    }

    public PrimaryExpression subject() {
        return subject;
    }

    public Expression index() {
        return index;
    }

    //


    @Override
    public String toString() {
        return subject + "[" + index + "]";
    }
}
