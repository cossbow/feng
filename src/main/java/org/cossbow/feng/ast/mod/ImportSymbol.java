package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;

public class ImportSymbol extends Entity {
    private final Identifier name;
    private final Optional<Identifier> alias;

    public ImportSymbol(Position pos,
                        Identifier name,
                        Optional<Identifier> alias) {
        super(pos);
        this.name = name;
        this.alias = alias;
    }

    public Identifier name() {
        return name;
    }

    public Optional<Identifier> alias() {
        return alias;
    }
}
