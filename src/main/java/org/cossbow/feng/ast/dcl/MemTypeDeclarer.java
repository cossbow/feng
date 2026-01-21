package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.gen.MemType;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;

public class MemTypeDeclarer extends TypeDeclarer
        implements Referable {
    private MemType type;
    private Refer ref;

    public MemTypeDeclarer(Position pos,
                           MemType type,
                           Refer refer) {
        super(pos);
        this.type = type;
        this.ref = refer;
    }

    public boolean readonly() {
        return type.readonly();
    }

    public Refer ref() {
        return ref;
    }

    @Override
    public Optional<Refer> refer() {
        return Optional.of(ref);
    }

    public Optional<TypeDeclarer> mapped() {
        return type.mapped();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MemTypeDeclarer t))
            return false;

        return type.equals(t.type) &&
                ref.equals(t.ref);
    }

}
