package org.cossbow.feng.ast;

import java.util.Set;

public enum BinaryOperator {

    // arithmetic
    POW("^"),
    MUL("*"), DIV("/"), MOD("%"),
    ADD("+"), SUB("-"),

    // bit
    LSHIFT("<<"), RSHIFT(">>"),
    BITAND("&"),
    BITXOR("~"),
    BITOR("|"),

    // relation
    EQ("=="), NE("!="),
    GT(">"), LT("<"),
    GE(">="), LE("<="),

    // bool
    AND("&&"),
    OR("||"),

    ;

    public final String code;

    BinaryOperator(String code) {
        this.code = code;
    }


    //

    @Override
    public String toString() {
        return code;
    }


    //

    public static final Set<BinaryOperator> SetMath = Set.of(POW, MUL, DIV, MOD, ADD, SUB);
    public static final Set<BinaryOperator> SetBits = Set.of(LSHIFT, RSHIFT, BITAND, BITXOR, BITOR);
    public static final Set<BinaryOperator> SetRel = Set.of(EQ, NE, GT, LT, LE, GE);
    public static final Set<BinaryOperator> SetLogic = Set.of(EQ, NE, AND, OR, BITAND, BITOR);

    public static final Set<BinaryOperator> SetEquals = Set.of(EQ, NE);

    public static final Set<BinaryOperator> Overridable = Set.of(
            ADD, SUB, MUL, DIV, MOD, POW, EQ, NE, GT, LT, LE, GE);

}
