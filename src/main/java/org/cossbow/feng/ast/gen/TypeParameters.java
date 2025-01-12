package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;

import java.util.List;

public class TypeParameters extends Entity {
    private final List<TypeParameter> params;

    public TypeParameters(Position pos,
                          List<TypeParameter> params) {
        super(pos);
        this.params = params;
    }

    public List<TypeParameter> params() {
        return params;
    }

    public boolean isEmpty() {
        return params.isEmpty();
    }

    //

    public static final TypeParameters EMPTY =
            new TypeParameters(Position.ZERO, List.of());
}
