package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;

/**
 * 临时，不在AST上
 */
public class DefinitionDeclarer extends TypeDeclarer {
    private final TypeDefinition def;

    public DefinitionDeclarer(Position pos,
                              TypeDefinition def) {
        super(pos);
        this.def = def;
    }

    public TypeDefinition def() {
        return def;
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefinitionDeclarer t))
            return false;
        return def.equals(t.def);
    }

    @Override
    public int hashCode() {
        return def.hashCode();
    }

    //

    @Override
    public String toString() {
        return def.toString();
    }
}
