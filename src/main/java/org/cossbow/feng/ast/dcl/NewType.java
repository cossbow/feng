package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

/**
 * The type infomation used for {@code new} an instance
 */
abstract
public class NewType extends Entity {
    public NewType(Position pos) {
        super(pos);
    }
}
