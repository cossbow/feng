package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.util.Optional;

import java.util.ArrayList;
import java.util.List;

/**
 * Type definition for implementing OOP
 */
abstract sealed
public class ObjectDefinition extends TypeDefinition
        permits ClassDefinition, InterfaceDefinition {
    public ObjectDefinition(Position pos,
                            Modifier modifier,
                            Symbol symbol,
                            TypeParameters generic,
                            TypeDomain domain) {
        super(pos, modifier, symbol, generic, domain);
    }

    abstract
    public List<DerivedType> supers();

    abstract
    public IdentifierMap<? extends Method> methods();

    abstract
    public IdentifierMap<? extends Method> allMethods();

    public Optional<? extends Method> method(Identifier name) {
        var m = allMethods();
        if (m.isEmpty()) return m.tryGet(name);
        return methods().tryGet(name);
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

}
