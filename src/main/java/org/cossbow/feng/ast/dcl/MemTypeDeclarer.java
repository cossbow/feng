package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.gen.MemType;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;

import java.util.Objects;

public class MemTypeDeclarer extends TypeDeclarer
        implements Referable {
    private final MemType type;
    private final Refer ref;

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

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + ref.hashCode();
    }

    //


    @Override
    public String toString() {
        return ref.toString() + type;
    }
}
