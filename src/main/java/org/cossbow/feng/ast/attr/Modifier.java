package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.SymbolMap;

public class Modifier extends Entity {
    private boolean export;
    private SymbolMap<Attribute> attributes;

    public Modifier(Position pos, boolean export,
                    SymbolMap<Attribute> attributes) {
        super(pos);
        this.export = export;
        this.attributes = attributes;
    }

    public boolean export() {
        return export;
    }

    public SymbolMap<Attribute> attributes() {
        return attributes;
    }


    //

    public static Modifier empty() {
        return new Modifier(Position.ZERO, false,
                new SymbolMap<>());
    }
}
