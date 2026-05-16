package org.cossbow.feng.ast.dcl;

public enum ReferKind {
    STRONG('*'),
    PHANTOM('&'),
    ;

    public final char symbol;

    ReferKind(char symbol) {
        this.symbol = symbol;
    }
}
