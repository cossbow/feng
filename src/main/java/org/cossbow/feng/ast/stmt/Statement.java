package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

/**
 * Statement root type
 */
abstract
public class Statement extends Entity {
    public Statement(Position pos) {
        super(pos);
    }

    /**
     * Deep copy for variadic inline expansion.
     * Each expansion gets independent mutable state.
     */
    public abstract Statement mirror();

}
