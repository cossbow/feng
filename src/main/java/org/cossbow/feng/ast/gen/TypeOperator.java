package org.cossbow.feng.ast.gen;

public enum TypeOperator {
    AND('&'),
    OR('|'),
    ;

    public final char symbol;

    TypeOperator(char symbol) {
        this.symbol = symbol;
    }
}
