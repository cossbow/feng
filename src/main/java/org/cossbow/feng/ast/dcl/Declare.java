package org.cossbow.feng.ast.dcl;

public enum Declare {
    VAR("var"),
    CONST("const"),
    ;

    public final String code;

    Declare(String code) {
        this.code = code;
    }

    //

    @Override
    public String toString() {
        return code;
    }
}
