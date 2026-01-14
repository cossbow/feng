package org.cossbow.feng.ast;

public class Identifier extends Entity {
    private String value;

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
        return o instanceof Identifier t
                && value.equals(t.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
