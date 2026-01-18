package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.DefinedType;

import java.util.Objects;

public class DefinedTypeDeclarer extends TypeDeclarer
        implements Referable {
    private DefinedType definedType;
    private Optional<Refer> refer;

    private transient Optional<TypeDefinition> definition;

    public DefinedTypeDeclarer(Position pos,
                               DefinedType definedType,
                               Optional<Refer> refer) {
        this(pos, definedType, refer, Optional.empty());
    }

    public DefinedTypeDeclarer(Position pos,
                               DefinedType definedType,
                               Optional<Refer> refer,
                               Optional<TypeDefinition> definition) {
        super(pos);
        this.definedType = definedType;
        this.refer = refer;
        this.definition=definition;
    }

    public DefinedType definedType() {
        return definedType;
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
