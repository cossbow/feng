package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.IdentifierTable;

public class Modifier extends Entity {
    private IdentifierTable<Attribute> attributes;

    public Modifier(Position pos,
                    IdentifierTable<Attribute> attributes) {
        super(pos);
        this.attributes = attributes;
    }

    public IdentifierTable<Attribute> attributes() {
        return attributes;
    }

    public static Modifier empty() {
        return new Modifier(Position.ZERO, new IdentifierTable<>());
    }
}
