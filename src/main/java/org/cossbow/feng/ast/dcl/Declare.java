package org.cossbow.feng.ast.dcl;

/**
 * Use to declare variables and class fields.
 */
public enum Declare {
    /**
     * Declare a mutable variable
     */
    VAR("var"),
    /**
     * Declare a immutable variable
     */
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
