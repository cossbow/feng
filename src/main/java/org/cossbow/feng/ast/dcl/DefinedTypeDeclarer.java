package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.DefinedType;

import java.util.Objects;

public class DefinedTypeDeclarer extends TypeDeclarer {
    private DefinedType definedType;
    private Optional<Reference> reference;

    public DefinedTypeDeclarer(Position pos,
                               DefinedType definedType,
                               Optional<Reference> reference) {
        super(pos);
        this.definedType = definedType;
        this.reference = reference;
    }

    public DefinedType definedType() {
        return definedType;
    }

    public Optional<Reference> reference() {
        return reference;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefinedTypeDeclarer that)) return false;
        return Objects.equals(definedType, that.definedType)
                && Objects.equals(reference, that.reference);
    }

}
