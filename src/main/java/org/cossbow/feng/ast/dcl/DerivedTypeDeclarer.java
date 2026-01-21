package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;

public class DerivedTypeDeclarer extends TypeDeclarer
        implements Referable {
    private DerivedType derivedType;
    private Optional<Refer> refer;

    private transient Optional<TypeDefinition> definition;

    public DerivedTypeDeclarer(Position pos,
                               DerivedType derivedType,
                               Optional<Refer> refer) {
        this(pos, derivedType, refer, Optional.empty());
    }

    public DerivedTypeDeclarer(Position pos,
                               DerivedType derivedType,
                               Optional<Refer> refer,
                               Optional<TypeDefinition> definition) {
        super(pos);
        this.derivedType = derivedType;
        this.refer = refer;
        this.definition = definition;
    }

    public DerivedType derivedType() {
        return derivedType;
    }

    public Optional<Refer> refer() {
        return refer;
    }

    public Optional<TypeDefinition> definition() {
        return definition;
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DerivedTypeDeclarer t))
            return false;
        return derivedType.equals(t.derivedType)
                && refer.equals(t.refer);
    }

    @Override
    public String toString() {
        if (refer.none()) return derivedType.toString();
        var sb = new StringBuilder(16);
        sb.append(refer.get().kind().symbol);
        if (refer.get().required()) sb.append('!');
        if (refer.get().immutable()) sb.append('#');
        sb.append(derivedType.toString());
        return sb.toString();
    }
}
