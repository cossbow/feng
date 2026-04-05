package org.cossbow.feng.ast;

import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.util.Optional;

public class Symbol extends Entity {
    private final Optional<ModulePath> module;
    private final Identifier name;

    public Symbol(Position pos,
                  Optional<ModulePath> module,
                  Identifier name) {
        super(pos);
        this.module = module;
        this.name = name;
    }

    public Symbol(Position pos, Identifier name) {
        this(pos, Optional.empty(), name);
    }

    public Symbol(Identifier name) {
        this(name.pos(), Optional.empty(), name);
    }

    public Optional<ModulePath> module() {
        return module;
    }

    public Identifier name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Symbol s &&
                module.equals(s.module) &&
                name.equals(s.name);
    }

    @Override
    public int hashCode() {
        return 31 * module.hashCode() + name.hashCode();
    }

    @Override
    public String toString() {
        if (module.none()) return name.toString();
        return module.get().name().toString() +
                DELIMITER + name.toString();
    }

    //

    public static final char DELIMITER = '$';

}
