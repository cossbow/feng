package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;

import java.util.List;

public class Import extends Entity {
    private List<Identifier> path;
    private Optional<Identifier> alias;
    private boolean flat;

    public Import(Position pos,
                  List<Identifier> path,
                  Optional<Identifier> alias,
                  boolean flat) {
        super(pos);
        this.path = path;
        this.alias = alias;
        this.flat = flat;
    }

    public List<Identifier> path() {
        return path;
    }

    public Identifier module() {
        return alias.getOrElse(path.getLast());
    }

    public Optional<Identifier> alias() {
        return alias;
    }

    public boolean flat() {
        return flat;
    }
}
