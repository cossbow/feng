package org.cossbow.feng.ast.struct;

import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

public class StructureField extends Field {
    private Optional<Expression> bitfield;

    public StructureField(Position pos,
                          Identifier name,
                          Optional<Expression> bitfield,
                          TypeDeclarer type) {
        super(pos, name, type);
        this.bitfield = bitfield;
    }

    public Optional<Expression> bitfield() {
        return bitfield;
    }

    private volatile int bits;

    public int bits() {
        return bits;
    }

    public void bits(int bits) {
        this.bits = bits;
    }

    //

    private final Lazy<StructureDefinition> master=Lazy.nil();

    public Lazy<StructureDefinition> master() {
        return master;
    }
    //

    @Override
    public String toString() {
        if (bitfield.none()) return super.toString();
        return name() + ":" + bitfield.get();
    }
}
