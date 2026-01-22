package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.MemDefinition;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Optional;

import java.util.Objects;

public class MemType extends DefinedType {
    private final boolean readonly;
    private final Optional<TypeDeclarer> mapped;

    public MemType(Position pos,
                   boolean readonly,
                   Optional<TypeDeclarer> mapped) {
        super(pos, MemDefinition.get(readonly).symbol().name());
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
    public boolean equals(Object o) {
        if (!(o instanceof MemType t))
            return false;
        return readonly == t.readonly &&
                mapped.equals(t.mapped);
    }

    @Override
    public int hashCode() {
        return 31 * Boolean.hashCode(readonly)
                + mapped.hashCode();
    }

    //


    @Override
    public String toString() {
        var md = MemDefinition.get(readonly);
        if (mapped.none()) return md.symbol().toString();
        return md.symbol() + "`" + mapped.get() + "`";
    }
}
