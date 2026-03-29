package org.cossbow.feng.ast.dcl;

public enum ReferKind {
    // 强引用
    STRONG('*'),
    // 虚引用
    PHANTOM('&'),
    // 恒引用
    ETERNAL('$'),
    ;

    public final char symbol;

    ReferKind(char symbol) {
        this.symbol = symbol;
    }
}
