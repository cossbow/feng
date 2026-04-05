package org.cossbow.feng.ast.mod;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Optional;

public class Import extends Entity {
    private ModulePath path;
    private Optional<Identifier> alias;

    public Import(Position pos,
                  ModulePath path,
                  Optional<Identifier> alias) {
        super(pos);
        this.path = path;
        this.alias = alias;
    }

    public Identifier name() {
        return alias.getOrElse(path.name());
    }

    public ModulePath path() {
        return path;
    }

    public Optional<Identifier> alias() {
        return alias;
    }

    //

    @Override
    public final boolean equals(Object o) {
        return o instanceof Import i &&
                path.equals(i.path) &&
                alias.equals(i.alias);

    }

    @Override
    public int hashCode() {
        int result = path.hashCode();
        result = 31 * result + alias.hashCode();
        return result;
    }

    //
    @Override
    public String toString() {
        if (alias.none())
            return path.toString();
        return path + " " + alias.get();
    }
}
