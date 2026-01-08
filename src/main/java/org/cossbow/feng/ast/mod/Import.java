package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;

import java.util.List;

public class Import extends Entity {
    private List<Identifier> module;
    private Optional<Identifier> alias;
    private boolean flat;

    public Import(Position pos,
                  List<Identifier> module,
                  Optional<Identifier> alias,
                  boolean flat) {
        super(pos);
        this.module = module;
        this.alias = alias;
        this.flat = flat;
    }

    public List<Identifier> module() {
        return module;
    }

    public Optional<Identifier> alias() {
        return alias;
    }

    public boolean flat() {
        return flat;
    }
}
