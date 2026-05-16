package org.cossbow.feng.ast.expr;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.NewType;

/**
 * Creating an instance dynamically, the return type is a strong
 * reference to the corresponding primitive type.
 * <p>
 * {@code new(int)}, {@code new(int, 1)}
 * <p>
 * {@code new(Car)}, {@code new(Car, {id=1})}
 * <p>
 * {@code new([n]int)}, {@code new([n]int, [1])}
 */
public class NewExpression extends PrimaryExpression {
    private final NewType type;
    private final Optional<Expression> arg;

    public NewExpression(Position pos,
                         NewType type,
                         Optional<Expression> arg) {
        super(pos);
        this.type = type;
        this.arg = arg;
    }

    public NewType type() {
        return type;
    }

    public Optional<Expression> arg() {
        return arg;
    }

    /**
     * The dynamically created instance is unbound
     */
    @Override
    public boolean unbound() {
        return true;
    }

    //

    @Override
    public String toString() {
        if (arg.none()) return "new(" + type + ")";
        return "new(" + type + ", " + arg.get() + ")";
    }
}
