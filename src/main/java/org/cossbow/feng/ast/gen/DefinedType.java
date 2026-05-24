package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;

/**
 * A symbol link to a {@link TypeDefinition} or a gengeric type paramster
 */
abstract
public class DefinedType extends Entity {
    public DefinedType(Position pos) {
        super(pos);
    }

    abstract
    public Identifier name();

    @Override
    abstract
    public boolean equals(Object o);

    @Override
    abstract
    public int hashCode();

}
