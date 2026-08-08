package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.SymbolMap;
import org.cossbow.feng.util.Optional;

public class Modifier extends Entity {
    /**
     * Export to other modules for use
     */
    private final boolean export;
    /**
     * List of attributes used for decoration
     */
    private final SymbolMap<Attribute> attributes;

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


    public Optional<Attribute> sync() {
        return attributes.tryGet(AttributeDefinition.SyncDef.symbol());
    }

    public Optional<Attribute> async() {
        return attributes.tryGet(AttributeDefinition.AsyncDef.symbol());
    }

    //

    public static Modifier empty() {
        return new Modifier(Position.ZERO, false,
                new SymbolMap<>());
    }

    public static Modifier empty(boolean export) {
        return new Modifier(Position.ZERO, export,
                new SymbolMap<>());
    }
}
