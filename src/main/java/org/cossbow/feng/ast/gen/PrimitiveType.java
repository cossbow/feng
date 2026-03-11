package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Primitive;

public class PrimitiveType extends DefinedType {
    private final Identifier name;
    private final Primitive primitive;

    public PrimitiveType(Position pos,
                         Identifier name,
                         Primitive primitive) {
        super(pos);
        this.primitive = primitive;
        this.name = name;
    }

    public Primitive primitive() {
        return primitive;
    }

    public Identifier name() {
        return name;
    }

    //

    @Override
    public boolean equals(Object o) {
        return o instanceof PrimitiveType t
                && primitive == t.primitive;
    }

    @Override
    public int hashCode() {
        return primitive.hashCode();
    }

    //


    @Override
    public String toString() {
        return primitive.toString();
    }
}
