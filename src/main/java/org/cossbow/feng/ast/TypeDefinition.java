package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

abstract
public class TypeDefinition extends Definition {
    private TypeDomain domain;

    public TypeDefinition(Position pos,
                          Modifier modifier,
                          Identifier name,
                          TypeParameters generic,
                          TypeDomain domain) {
        super(pos, modifier, name, generic);
        this.domain = domain;
    }

    public TypeDomain domain() {
        return domain;
    }

    @Override
    public String toString() {
        return name().value();
    }
}
