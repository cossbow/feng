package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.lit.BoolLiteral;
import org.cossbow.feng.ast.lit.FloatLiteral;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.lit.Literal;

/**
 * 临时，不在AST上
 */
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

    public boolean isInteger() {
        return literal instanceof IntegerLiteral;
    }

    public boolean isFloat() {
        return literal instanceof FloatLiteral;
    }

    public boolean isBool() {
        return literal instanceof BoolLiteral;
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LiteralTypeDeclarer t)) return false;
        return literal.getClass().equals(t.literal.getClass());
    }

}
