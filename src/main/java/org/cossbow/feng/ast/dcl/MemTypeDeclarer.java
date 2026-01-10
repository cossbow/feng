package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;

import java.util.Map;

public class MemTypeDeclarer extends TypeDeclarer {
    private boolean readonly;
    private Optional<TypeDeclarer> mapped;

    public MemTypeDeclarer(Position pos,
                           boolean readonly,
                           Optional<TypeDeclarer> mapped) {
        super(pos);
        this.readonly = readonly;
        this.mapped = mapped;
    }

    public boolean readonly() {
        return readonly;
    }

    public Optional<TypeDeclarer> mapped() {
        return mapped;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof MemTypeDeclarer mtd) {
            return readonly == mtd.readonly;
        }
        return false;
    }

    public static final Map<String, Boolean> TYPES = Map.of(
            "ram", false,
            "rom", true
    );
}
