package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Optional;

public class PrimitiveTypeDeclarer extends TypeDeclarer
        implements Referable {
    private final Primitive primitive;
    private final Optional<Refer> refer;

    public PrimitiveTypeDeclarer(Position pos,
                                 Primitive primitive,
                                 Optional<Refer> refer) {
        super(pos);
        this.primitive = primitive;
        this.refer = refer;
    }

    public Primitive primitive() {
        return primitive;
    }

    @Override
    public Optional<Refer> refer() {
        return refer;
    }

    public boolean isBool() {
        return primitive.isBool();
    }

    public boolean isInteger() {
        return primitive.isInteger();
    }

    public Optional<TypeDeclarer> derefer() {
        if (refer.none()) return Optional.of(this);
        var n = new PrimitiveTypeDeclarer(pos(), primitive,
                Optional.empty());
        return Optional.of(n);
    }

    @Override
    public boolean baseTypeSame(TypeDeclarer td) {
        return td instanceof PrimitiveTypeDeclarer t
                && primitive == t.primitive;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PrimitiveTypeDeclarer t
                && primitive == t.primitive
                && refer.equals(t.refer);

    }

    @Override
    public int hashCode() {
        return primitive.hashCode();
    }

    //

    @Override
    public String toString() {
        if (refer.none()) return primitive.code;
        return refer.get() + primitive.code;
    }
}
