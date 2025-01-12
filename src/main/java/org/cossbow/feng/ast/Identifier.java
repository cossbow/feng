package org.cossbow.feng.ast;

import java.util.Objects;

public class Identifier extends Entity {
    private final String value;

    public Identifier(Position pos, String value) {
        super(pos);
        this.value = value;
    }

    public String value() {
        return value;
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Identifier that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
