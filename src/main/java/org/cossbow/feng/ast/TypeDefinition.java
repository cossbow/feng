package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;

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

    public boolean newable() {
        return false;
    }

    //

    public DerivedType link(TypeArguments tArgs) {
        var dt = new DerivedType(Position.ZERO,
                symbol(), tArgs);
        dt.def(this);
        dt.gm(GenericMap.make(this, generic(), tArgs));
        return dt;
    }

    public DerivedType link() {
        return link(TypeArguments.EMPTY);
    }

    //

    @Override
    public String toString() {
        return domain.name + ' ' + symbol() + generic();
    }
}
