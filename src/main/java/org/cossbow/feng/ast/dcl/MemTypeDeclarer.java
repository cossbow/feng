package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.gen.MemType;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;

public class MemTypeDeclarer extends TypeDeclarer
        implements Referable {
    private MemType type;
    private Refer refer;

    public MemTypeDeclarer(Position pos,
                           MemType type,
                           Refer refer) {
        super(pos);
        this.type = type;
        this.refer = refer;
    }

    public boolean readonly() {
        return type.readonly();
    }

    @Override
    public Optional<Refer> refer() {
        return Optional.of(refer);
    }

    public Optional<TypeDeclarer> mapped() {
        return type.mapped();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MemTypeDeclarer t))
            return false;

        return type.equals(t.type) &&
                refer.equals(t.refer);
    }

}
