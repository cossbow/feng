package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;

import java.util.Map;

public class MemTypeDeclarer extends TypeDeclarer
        implements Referable {
    private boolean readonly;
    private Optional<Refer> refer;
    private Optional<TypeDeclarer> mapped;

    public MemTypeDeclarer(Position pos,
                           boolean readonly,
                           Optional<Refer> refer,
                           Optional<TypeDeclarer> mapped) {
        super(pos);
        this.readonly = readonly;
        this.mapped = mapped;
        this.refer = refer;
    }

    public boolean readonly() {
        return readonly;
    }

    @Override
    public Optional<Refer> refer() {
        return Optional.empty();
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
