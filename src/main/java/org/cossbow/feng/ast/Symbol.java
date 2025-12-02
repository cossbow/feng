package org.cossbow.feng.ast;

import java.util.Objects;

public class Symbol extends Entity {
    private final Optional<Identifier> module;
    private final Identifier name;

    public Symbol(Position pos,
                  Optional<Identifier> module,
                  Identifier name) {
        super(pos);
        this.module = module;
        this.name = name;
    }

    public Symbol(Position pos, Identifier name) {
        this(pos, Optional.empty(), name);
    }

    public Optional<Identifier> module() {
        return module;
    }

    public Identifier name() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Symbol symbol)) return false;
        return Objects.equals(module, symbol.module) &&
                Objects.equals(name, symbol.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(module, name);
    }

    @Override
    public String toString() {
        if (module.none()) return name.toString();
        return module.get().toString() +
                "$" + name.toString();
    }
}
