package org.cossbow.feng.ast;

import org.antlr.v4.runtime.Token;

public record Position(Token start, Token stop) {

    @Override
    public String toString() {
        return "(" + start.getLine() + ","
                + start.getCharPositionInLine() + ")";
    }

    public static final Position ZERO = new Position(null, null);
}
