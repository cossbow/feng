package org.cossbow.feng.ast.struct;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.Optional;

public class StructureField extends StructureMember {
    private final Identifier name;
    private final Optional<Expression> bitfield;
    private final StructureType type;

    public StructureField(Position pos,
                          Identifier name,
                          Optional<Expression> bitfield,
                          StructureType type) {
        super(pos);
        this.name = name;
        this.bitfield = bitfield;
        this.type = type;
    }

    public Identifier name() {
        return name;
    }

    public Optional<Expression> bitfield() {
        return bitfield;
    }

    public StructureType type() {
        return type;
    }
}
