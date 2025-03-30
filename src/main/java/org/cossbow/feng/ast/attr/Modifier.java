package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.UniqueTable;

import java.util.List;

public class Modifier extends Entity {
    private final UniqueTable<Attribute> attributes;

    public Modifier(Position pos,
                    UniqueTable<Attribute> attributes) {
        super(pos);
        this.attributes = attributes;
    }

    public UniqueTable<Attribute> attributes() {
        return attributes;
    }

    public static Modifier empty() {
        return new Modifier(Position.ZERO, new UniqueTable<>());
    }
}
