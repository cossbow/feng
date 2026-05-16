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

    //

}
