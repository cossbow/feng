package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Exportable;
import org.cossbow.feng.ast.Position;

abstract
public class Global extends Entity implements Exportable {
    private final boolean export;

    public Global(Position pos, boolean export) {
        super(pos);
        this.export = export;
    }

    @Override
    public boolean export() {
        return export;
    }
}
