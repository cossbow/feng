package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.oop.ClassDefinition;

abstract
public class TypeDefinition extends Definition {
    private TypeDomain domain;

    public TypeDefinition(Position pos,
                          Modifier modifier,
                          Symbol symbol,
                          TypeParameters generic,
                          TypeDomain domain) {
        super(pos, modifier, symbol, generic);
        this.domain = domain;
    }

    public TypeDomain domain() {
        return domain;
    }

    public <D extends TypeDefinition> boolean same(D o) {
        return symbol().equals(o.symbol());
    }

    @Override
    public String toString() {
        return domain.name + ' ' + symbol();
    }
}
