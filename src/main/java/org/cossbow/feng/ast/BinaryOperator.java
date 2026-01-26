package org.cossbow.feng.ast;

import java.util.EnumSet;
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

    public static final Set<BinaryOperator> SetMath = EnumSet.of(POW, MUL, DIV, MOD, ADD, SUB);
    public static final Set<BinaryOperator> SetBits = EnumSet.of(LSHIFT, RSHIFT, BITAND, BITXOR, BITOR);
    public static final Set<BinaryOperator> SetRel = EnumSet.of(EQ, NE, GT, LT, LE, GE);
    public static final Set<BinaryOperator> SetLogic = EnumSet.of(EQ, NE, AND, OR, BITAND, BITOR);

    public static final Set<BinaryOperator> SetEquals = EnumSet.of(EQ, NE);
}
