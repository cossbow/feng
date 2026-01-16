package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.lit.Literal;

public class LiteralTypeDeclarer extends TypeDeclarer {
    private Literal literal;

    public LiteralTypeDeclarer(Position pos,
                               Literal literal) {
        super(pos);
        this.literal = literal;
    }

    public Literal literal() {
        return literal;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LiteralTypeDeclarer t)) return false;
        return literal.getClass().equals(t.literal.getClass());
    }

}
