package org.cossbow.feng.ast;

import org.cossbow.feng.util.Optional;

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
        return o instanceof Symbol s &&
                module.equals(s.module) &&
                name.equals(s.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(module, name);
    }

    @Override
    public String toString() {
        if (module.none()) return name.toString();
        return module.get().toString() +
                DELIMITER + name.toString();
    }

    //

    public static final char DELIMITER = '$';

}
