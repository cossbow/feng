package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.gen.*;

import java.util.ArrayList;

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

    public boolean syncable() {
        return true;
    }

    public DerivedType link(Position pos, TypeArguments tArgs) {
        var dt = new DerivedType(pos, symbol(), tArgs);
        dt.def(this);
        dt.gm(GenericMap.make(this, generic(), tArgs));
        return dt;
    }

    public DerivedType link(TypeArguments tArgs) {
        return link(pos(), tArgs);
    }

    public DerivedType link(Position pos) {
        if (generic().isEmpty())
            return link(pos, TypeArguments.EMPTY);
        // 泛型类型需要带上形参
        var args = new ArrayList<TypeDeclarer>(generic().size());
        for (var tp : generic()) {
            args.add(new GenericTypeDeclarer(pos,
                    new GenericType(pos, tp)));
        }
        return link(pos, new TypeArguments(pos, args));
    }

    public DerivedType link() {
        return link(TypeArguments.EMPTY);
    }

    public DerivedTypeDeclarer refer(Position pos, ReferKind kind) {
        return new DerivedTypeDeclarer(pos, link(),
                new Refer(pos, kind, true, false));
    }

    //

    @Override
    public String toString() {
        return domain.name + ' ' + symbol() + generic();
    }
}
