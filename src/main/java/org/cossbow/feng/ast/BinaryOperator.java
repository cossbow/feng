package org.cossbow.feng.ast;

public enum BinaryOperator {

    // arithmetic
    POW(Kind.MATH),
    MUL(Kind.MATH), DIV(Kind.MATH), MOD(Kind.MATH),
    ADD(Kind.MATH), SUB(Kind.MATH),

    // bit
    LSHIFT(Kind.BIT), RSHIFT(Kind.BIT),
    BITAND(Kind.BIT),
    BITXOR(Kind.BIT),
    BITOR(Kind.BIT),

    // relation
    EQ(Kind.REL), NE(Kind.REL),
    GT(Kind.REL), LT(Kind.REL),
    LE(Kind.REL), GE(Kind.REL),

    // bool
    AND(Kind.BOOL),
    OR(Kind.BOOL),

    ;

    public final Kind kind;

    BinaryOperator(Kind kind) {
        this.kind = kind;
    }

    //

    public enum Kind {
        MATH,
        BIT,
        REL,
        BOOL,
    }
}
