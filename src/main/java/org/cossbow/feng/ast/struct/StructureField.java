package org.cossbow.feng.ast.struct;

import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.SymbolMap;
import org.cossbow.feng.ast.attr.Attribute;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

public class StructureField extends Field {
    private Optional<Expression> bitfield;
    private final SymbolMap<Attribute> attributes;

    public StructureField(Position pos,
                          Identifier name,
                          Optional<Expression> bitfield,
                          TypeDeclarer type) {
        this(pos, name, bitfield, type, new SymbolMap<>());
    }

    public StructureField(Position pos,
                          Identifier name,
                          Optional<Expression> bitfield,
                          TypeDeclarer type,
                          SymbolMap<Attribute> attributes) {
        super(pos, name, type);
        this.bitfield = bitfield;
        this.attributes = attributes;
    }

    public Optional<Expression> bitfield() {
        return bitfield;
    }

    public SymbolMap<Attribute> attributes() {
        return attributes;
    }

    private volatile int bits;

    public int bits() {
        return bits;
    }

    public void bits(int bits) {
        this.bits = bits;
    }

    // @Align({value=n}) — 覆盖自然对齐
    private int align = 0;

    public int align() {
        return align;
    }

    public void align(int align) {
        this.align = align;
    }

    //

    private final Lazy<StructureDefinition> master = Lazy.nil();

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
