package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Lazy;

abstract
public class Statement extends Entity {
    public Statement(Position pos) {
        super(pos);
    }

    public final Lazy<TempBox> tempBox = Lazy.nil();
}
