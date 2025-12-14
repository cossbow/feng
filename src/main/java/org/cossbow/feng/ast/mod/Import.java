package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;

public class Import extends Entity {
    private final Module_ module;
    private final Optional<Identifier> alias;
    private final boolean flat;

    public Import(Position pos,
                  Module_ module,
                  Optional<Identifier> alias,
                  boolean flat) {
        super(pos);
        this.module = module;
        this.alias = alias;
        this.flat = flat;
    }

    public Module_ module() {
        return module;
    }

    public Optional<Identifier> alias() {
        return alias;
    }

    public boolean flat() {
        return flat;
    }
}
