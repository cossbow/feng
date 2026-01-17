package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;

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
        return refer;
    }

    public Optional<TypeDeclarer> mapped() {
        return mapped;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MemTypeDeclarer t))
            return false;

        return readonly == t.readonly &&
                refer.equals(t.refer);
    }

}
