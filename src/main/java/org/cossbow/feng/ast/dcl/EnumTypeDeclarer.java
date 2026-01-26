package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.EnumDefinition;
import org.cossbow.feng.ast.Position;

public class EnumTypeDeclarer extends TypeDeclarer {
    private final EnumDefinition def;

    public EnumTypeDeclarer(Position pos,
                            EnumDefinition def) {
        super(pos);
        this.def = def;
    }

    public EnumDefinition def() {
        return def;
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof EnumTypeDeclarer t
                && def.equals(t.def);

    }

    @Override
    public int hashCode() {
        return def.hashCode();
    }

    //

    @Override
    public String toString() {
        return def.symbol().toString();
    }
}
