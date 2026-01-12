package org.cossbow.feng.ast.dcl;

public enum ReferKind {
    STRONG('*'),
    PHANTOM('&'),
    WEAK('~'),
    ;

    public final char symbol;

    ReferKind(char symbol) {
        this.symbol = symbol;
    }
}
