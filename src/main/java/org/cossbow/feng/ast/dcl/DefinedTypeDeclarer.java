package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.DefinedType;

import java.util.Objects;

public class DefinedTypeDeclarer extends TypeDeclarer
        implements Referable {
    private DefinedType definedType;
    private Optional<Refer> refer;

    public DefinedTypeDeclarer(Position pos,
                               DefinedType definedType,
                               Optional<Refer> refer) {
        super(pos);
        this.definedType = definedType;
        this.refer = refer;
    }

    public DefinedType definedType() {
        return definedType;
    }

    public Optional<Refer> refer() {
        return refer;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefinedTypeDeclarer t))
            return false;
        return definedType.equals(t.definedType)
                && refer.equals(t.refer);
    }

    @Override
    public String toString() {
        if (refer.none()) return definedType.toString();
        var sb = new StringBuilder(16);
        sb.append(refer.get().kind().symbol);
        if (refer.get().required()) sb.append('!');
        if (refer.get().immutable()) sb.append('#');
        sb.append(definedType.toString());
        return sb.toString();
    }
}
