package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.ErrorUtil;

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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TypeDefinition t))
            return false;
        if (!generic().isEmpty() || !t.generic().isEmpty())
            return ErrorUtil.unsupported("generic");
        return symbol().equals(t.symbol());
    }

    @Override
    public int hashCode() {
        if (!generic().isEmpty())
            return ErrorUtil.unsupported("generic");
        return symbol().hashCode();
    }

    //

    @Override
    public String toString() {
        return domain.name + ' ' + symbol();
    }
}
