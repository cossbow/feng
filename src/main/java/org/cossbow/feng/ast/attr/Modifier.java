package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

import java.util.List;

public class Modifier extends Entity {
    private final List<Attribute> attributes;

    public static final Modifier EMPTY = new Modifier(Position.ZERO, List.of());

    public List<Attribute> attributes() {
        return attributes;
    }

    //

    public Modifier(Position pos,
                    List<Attribute> attributes) {
        super(pos);
        this.attributes = attributes;
    }
}
