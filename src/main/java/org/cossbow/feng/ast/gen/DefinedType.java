package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.dcl.Refer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Optional;

/**
 * A symbol link to a {@link TypeDefinition} or a gengeric type paramster
 */
abstract sealed
public class DefinedType extends Entity
        permits DerivedType, GenericType, PrimitiveType {
    public DefinedType(Position pos) {
        super(pos);
    }

    abstract
    public Identifier name();

    abstract
    public TypeDeclarer declarer(Optional<Refer> r);

    @Override
    abstract
    public boolean equals(Object o);

    @Override
    abstract
    public int hashCode();

}
