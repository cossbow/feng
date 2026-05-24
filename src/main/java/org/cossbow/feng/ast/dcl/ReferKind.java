package org.cossbow.feng.ast.dcl;

/**
 * Reference Kind
 */
public enum ReferKind {
    /**
     * reference a escaped instance
     */
    STRONG('*'),
    /**
     * reference for a while
     */
    PHANTOM('&'),
    ;

    public final char symbol;

    ReferKind(char symbol) {
        this.symbol = symbol;
    }
}

