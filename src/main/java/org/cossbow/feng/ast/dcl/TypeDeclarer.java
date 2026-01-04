package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

abstract
public class TypeDeclarer extends Entity {
    public TypeDeclarer(Position pos) {
        super(pos);
    }

    abstract public boolean equals(Object obj);

}
