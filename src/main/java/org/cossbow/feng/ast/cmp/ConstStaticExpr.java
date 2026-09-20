package org.cossbow.feng.ast.cmp;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.GenericMap;

public class ConstStaticExpr extends StaticExpr {
    private final boolean value;

    public ConstStaticExpr(Position pos, boolean value) {
        super(pos);
        this.value = value;
    }

    public boolean value() {
        return value;
    }

    //

    @Override
    public StaticExpr mono(GenericMap gm) {
        return this;
    }

    @Override
    public boolean hasTypeVar() {
        return false;
    }

    @Override
    public boolean check() {
        return value;
    }
}
