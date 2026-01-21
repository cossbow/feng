package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.lit.*;

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

    public boolean isNil() {
        return literal instanceof NilLiteral;
    }

    public boolean isString() {
        return literal instanceof StringLiteral;
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LiteralTypeDeclarer t)) return false;
        return literal.getClass().equals(t.literal.getClass());
    }

    //


    @Override
    public String toString() {
        return literal.toString();
    }
}
