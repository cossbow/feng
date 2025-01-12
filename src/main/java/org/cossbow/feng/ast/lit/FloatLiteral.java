package org.cossbow.feng.ast.lit;

import org.cossbow.feng.ast.Position;

import java.math.BigDecimal;

public class FloatLiteral extends Literal {
    private final BigDecimal value;

    public FloatLiteral(Position pos, BigDecimal value) {
        super(pos);
        this.value = value;
    }

    public BigDecimal value() {
        return value;
    }
}
