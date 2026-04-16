package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.TypeDomain;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeParameters;

import java.util.stream.Stream;

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
    public Stream<? extends DerivedType> supers();
}
