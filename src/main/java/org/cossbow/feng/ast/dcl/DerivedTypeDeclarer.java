package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

public class DerivedTypeDeclarer extends TypeDeclarer
        implements Referable {
    private DerivedType derivedType;
    private Optional<Refer> refer;

    public DerivedTypeDeclarer(Position pos,
                               DerivedType derivedType,
                               Optional<Refer> refer) {
        super(pos);
        this.derivedType = derivedType;
        this.refer = refer;
    }

    public DerivedType derivedType() {
        return derivedType;
    }

    public Optional<Refer> refer() {
        return refer;
    }

    public Lazy<TypeDefinition> def = Lazy.nil();

    //

    public boolean baseTypeSame(TypeDeclarer td) {
        if (!(td instanceof DerivedTypeDeclarer t))
            return false;
        return derivedType.equals(t.derivedType);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DerivedTypeDeclarer t))
            return false;
        return derivedType.equals(t.derivedType)
                && refer.equals(t.refer);
    }

    @Override
    public int hashCode() {
        int result = derivedType.hashCode();
        result = 31 * result + refer.hashCode();
        return result;
    }
    //

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
