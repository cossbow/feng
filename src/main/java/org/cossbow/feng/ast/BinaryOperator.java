package org.cossbow.feng.ast;

public enum BinaryOperator {

    // arithmetic
    POW,
    MUL, DIV, MOD,
    ADD, SUB,

    // bit
    LSHIFT, RSHIFT,
    BITAND,
    BITXOR,
    BITOR,

    // relation
    EQ, NE,
    GT, LT,
    LE, GE,

    // bool
    AND,
    OR,

    ;

}
