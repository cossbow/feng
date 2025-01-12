package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.DefinedType;

public class DefinedTypeDeclarer extends TypeDeclarer {
    private final DefinedType definedType;
    private final boolean reference;
    private final boolean phantom;
    private final boolean optional;

    public DefinedTypeDeclarer(Position pos,
                               DefinedType definedType,
                               boolean reference,
                               boolean phantom,
                               boolean optional) {
        super(pos);
        this.definedType = definedType;
        this.reference = reference;
        this.phantom = phantom;
        this.optional = optional;
    }

    public DefinedTypeDeclarer(Position pos,
                               DefinedType definedType) {
        this(pos, definedType, false, false, false);
    }

    public DefinedType definedType() {
        return definedType;
    }

    public boolean pointer() {
        return reference;
    }

    public boolean phantom() {
        return phantom;
    }

    public boolean optional() {
        return optional;
    }
}
