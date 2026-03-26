package org.cossbow.feng.ast;

public class Identifier extends Entity {
    private String value;
    private boolean unnamed;

    public Identifier(Position pos, String value, boolean unnamed) {
        super(pos);
        this.value = value;
        this.unnamed = unnamed;
    }

    public Identifier(Position pos, String value) {
        this(pos, value, false);
    }

    public Identifier(String value) {
        this(Position.ZERO, value);
    }

    public String value() {
        return value;
    }

    public boolean unnamed() {
        return unnamed;
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
        if (unnamed) return "";
        return value;
    }
}
