package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Optional;

public class Import extends Entity {
    private ModulePath path;
    private Optional<Identifier> alias;
    private boolean flat;

    public Import(Position pos,
                  ModulePath path,
                  Optional<Identifier> alias,
                  boolean flat) {
        super(pos);
        this.path = path;
        this.alias = alias;
        this.flat = flat;
    }

    public ModulePath path() {
        return path;
    }

    public Identifier module() {
        return alias.getOrElse(path.values().getLast());
    }

    public Optional<Identifier> alias() {
        return alias;
    }

    public boolean flat() {
        return flat;
    }
}
