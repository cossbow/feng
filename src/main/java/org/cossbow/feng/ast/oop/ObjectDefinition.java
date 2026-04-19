package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeParameters;

import java.util.List;

abstract
public class ObjectDefinition extends TypeDefinition {
    public ObjectDefinition(Position pos,
                            Modifier modifier,
                            Symbol symbol,
                            TypeParameters generic,
                            TypeDomain domain) {
        super(pos, modifier, symbol, generic, domain);
    }

    abstract
    public int id();

    abstract
    public List<DerivedType> supers();

    abstract
    public IdentifierMap<? extends Method> methods();

    abstract
    public IdentifierMap<? extends Method> allMethods();

}
