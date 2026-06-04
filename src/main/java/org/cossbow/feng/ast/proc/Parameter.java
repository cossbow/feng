package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

/**
 * Parameters in {@link Prototype}
 * <p>
 * Fixed parameters ({@link FixedParameter}):
 * <p>
 * {@code func f(a int) }
 * <p>
 * {@code func f(a int, b bool) }
 * <p>
 * {@code func f(a,b int) }
 * <p>
 * {@code func f(int) }
 * <p>
 * {@code func f(int, bool) }
 * <p>
 * Variadic parameters ({@link VariadicParameter}):
 * <p>
 * {@code func f(args...) }
 * <p>
 * {@code func f(a int, args...) }
 * <p>
 * {@code func f(a,b int, args...) }
 * <p>
 * {@code func f(a int, b bool, args...) }
 */
abstract
public class Parameter extends Entity {
    public Parameter(Position pos) {
        super(pos);
    }

    @Override
    public Parameter clone() {
        return (Parameter) super.clone();
    }
}
