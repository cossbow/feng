package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.DefinedType;

public class DefinedTypeDeclarer extends TypeDeclarer {
    private final DefinedType definedType;
    private final Optional<Reference> reference;

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

}
