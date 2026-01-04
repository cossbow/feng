package org.cossbow.feng.ast;

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


    @Override
    public String toString() {
        return code;
    }
}
