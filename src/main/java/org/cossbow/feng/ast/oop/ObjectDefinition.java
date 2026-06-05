package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.Optional;

import java.util.List;

/**
 * Type definition for implementing OOP
 */
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

    public Optional<? extends Method> method(Identifier name) {
        var m = allMethods();
        if (m.isEmpty()) return m.tryGet(name);
        return methods().tryGet(name);
    }
}
