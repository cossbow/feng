package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.IdentifierTable;

public class TypeParameters extends Entity {
    private IdentifierTable<TypeParameter> params;

    public TypeParameters(Position pos,
                          IdentifierTable<TypeParameter> params) {
        super(pos);
        this.params = params;
    }

    public IdentifierTable<TypeParameter> params() {
        return params;
    }

    public TypeParameter get(Identifier name) {
        return params.get(name);
    }

    public boolean exists(Identifier name) {
        return params.exists(name);
    }

    public boolean isEmpty() {
        return params.isEmpty();
    }

    //

    public static TypeParameters empty() {
        return new TypeParameters(Position.ZERO, new IdentifierTable<>());
    }

}
