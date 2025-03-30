package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.UniqueTable;

public class TypeParameters extends Entity {
    private final UniqueTable<TypeParameter> params;

    public TypeParameters(Position pos,
                          UniqueTable<TypeParameter> params) {
        super(pos);
        this.params = params;
    }

    public UniqueTable<TypeParameter> params() {
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
        return new TypeParameters(Position.ZERO, new UniqueTable<>());
    }

}
