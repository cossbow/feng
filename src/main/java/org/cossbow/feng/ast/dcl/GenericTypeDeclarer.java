package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericType;
import org.cossbow.feng.ast.gen.TypeParameter;
import org.cossbow.feng.util.Optional;

/**
 * Use type variant as type
 */
public class GenericTypeDeclarer extends TypeDeclarer
        implements Referable {
    private final GenericType type;
    private final Optional<Refer> refer;

    public GenericTypeDeclarer(Position pos,
                               GenericType type,
                               Optional<Refer> refer) {
        super(pos);
        this.type = type;
        this.refer = refer;
    }

    public GenericTypeDeclarer(
            Position pos, GenericType type,
            Refer refer) {
        this(pos, type, Optional.of(refer));
    }

    public GenericTypeDeclarer(
            Position pos, GenericType type) {
        this(pos, type, Optional.empty());
    }

    public GenericType type() {
        return type;
    }

    public Optional<Refer> refer() {
        return refer;
    }

    public TypeParameter param() {
        return type.param();
    }

    public boolean hasTypeVar() {
        return true;
    }

    //

    @Override
    public Optional<TypeDeclarer> derefer() {
        if (refer.none()) return Optional.of(this);
        return Optional.of(new GenericTypeDeclarer(pos(), type));
    }


    //

    public boolean equals(Object o) {
        return o instanceof GenericTypeDeclarer t
                && type.equals(t.type);
    }

    public int hashCode() {
        return type.hashCode();
    }

    //

    @Override
    public String toString() {
        if (refer.none())
            return type.toString();
        return refer.get() + type.toString();
    }
}
