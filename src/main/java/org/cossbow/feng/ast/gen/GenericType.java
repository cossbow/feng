package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.GenericTypeDeclarer;
import org.cossbow.feng.ast.dcl.Refer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Optional;

/**
 * Type Variance: reference the type-paratemer in the generic-type
 */
final
public class GenericType extends DefinedType {
    private final TypeParameter param;

    public GenericType(Position pos,
                       TypeParameter param) {
        super(pos);
        this.param = param;
    }

    public TypeParameter param() {
        return param;
    }

    public Identifier name() {
        return param.name();
    }

    public TypeDeclarer declarer(Optional<Refer> r) {
        if (r.none())
            return new GenericTypeDeclarer(pos(), this);
        return new GenericTypeDeclarer(pos(), this,
                Optional.of(r.get().kind()),
                r.get().required(), r.get().unmodifiable());
    }

    public boolean equals(Object o) {
        return o instanceof GenericType t
                && param.equals(t.param);
    }

    public int hashCode() {
        return param.hashCode();
    }

    //

    @Override
    public String toString() {
        return param.toString();
    }
}
