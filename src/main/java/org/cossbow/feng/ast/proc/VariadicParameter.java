package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

/**
 * Variadic parameter will receive all arguments starting from
 * the current position on the right and can accept any type.
 * <p>
 * There can be at most one in the parameter list,
 * and it must be at the end.
 * <p>
 * {@code func f(args...)}
 */
public class VariadicParameter extends Parameter {
    /**
     * It has only one name,
     */
    private final Identifier name;

    public VariadicParameter(Position pos,
                             Identifier name) {
        super(pos);
        this.name = name;
    }

    public Identifier name() {
        return name;
    }

    //
    @Override
    public String toString() {
        return name + "...";
    }
}
