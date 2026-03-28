package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;

import java.util.concurrent.atomic.AtomicInteger;

public class Label extends Entity {
    private final Identifier name;

    public Label(Identifier name) {
        super(name.pos());
        this.name = name;
    }

    public Identifier name() {
        return name;
    }

    private final int id = IdGenerator.incrementAndGet();

    public int id() {
        return id;
    }

    //

    private static final AtomicInteger IdGenerator = new AtomicInteger(0);

    //


    @Override
    public boolean equals(Object o) {
        return o instanceof Label l &&
                name.equals(l.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    //
    @Override
    public String toString() {
        return name.toString();
    }
}
