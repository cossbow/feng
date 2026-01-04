package org.cossbow.feng.ast.struct;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

public class StructureField extends Entity {
    private Identifier name;
    private Optional<Expression> bitfield;
    private StructureType type;

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
