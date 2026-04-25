package org.cossbow.feng.ast;

import java.util.Set;

public enum UnaryOperator {
    POSITIVE("+"),
    NEGATIVE("-"),
    INVERT("!"),
    ;

    public final String code;

    UnaryOperator(String code) {
        this.code = code;
    }

    //

    public static final Set<UnaryOperator> Overridable =
            Set.of(POSITIVE, NEGATIVE, INVERT);

    //

    @Override
    public String toString() {
        return code;
    }
}
