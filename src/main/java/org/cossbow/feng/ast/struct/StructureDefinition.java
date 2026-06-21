package org.cossbow.feng.ast.struct;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.analysis.layout.StructureLayout;
import org.cossbow.feng.util.Lazy;

public class StructureDefinition extends TypeDefinition {
    private IdentifierMap<StructureField> fields;
    private boolean cType;

    public StructureDefinition(Position pos,
                               Modifier modifier,
                               Symbol symbol,
                               TypeParameters generic,
                               TypeDomain domain,
                               IdentifierMap<StructureField> fields,
                               boolean cType) {
        super(pos, modifier, symbol, generic, domain);
        this.fields = fields;
        this.cType = cType;
    }

    public StructureDefinition(Position pos,
                               Modifier modifier,
                               Symbol symbol,
                               TypeParameters generic,
                               TypeDomain domain,
                               IdentifierMap<StructureField> fields) {
        this(pos, modifier, symbol, generic, domain, fields, false);
    }

    public IdentifierMap<StructureField> fields() {
        return fields;
    }

    public boolean cType() {
        return cType;
    }

    public boolean newable() {
        return true;
    }

    //

    private int pack = 0;
    private long size = 1;
    private final Lazy<StructureLayout> layout = Lazy.nil();

    public int pack() {
        return pack;
    }

    public void pack(int pack) {
        this.pack = pack;
    }

    public long size() {
        return size;
    }

    public void size(long size) {
        this.size = size;
    }

    public Lazy<StructureLayout> layout() {
        return layout;
    }
}
