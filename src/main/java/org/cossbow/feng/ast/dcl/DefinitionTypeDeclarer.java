package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;

/**
 * 临时，不在AST上
 */
public class DefinitionTypeDeclarer extends TypeDeclarer {
    private final TypeDefinition definition;

    public DefinitionTypeDeclarer(Position pos,
                                  TypeDefinition definition) {
        super(pos);
        this.definition = definition;
    }

    public TypeDefinition definition() {
        return definition;
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefinitionTypeDeclarer t))
            return false;
        return definition.equals(t.definition);
    }

    @Override
    public int hashCode() {
        return definition.hashCode();
    }

    //

    @Override
    public String toString() {
        return definition.toString();
    }
}
