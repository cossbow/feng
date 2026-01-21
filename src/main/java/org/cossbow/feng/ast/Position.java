package org.cossbow.feng.ast;

import org.antlr.v4.runtime.Token;

public record Position(String file, Token start, Token stop) {

    @Override
    public String toString() {
        if (file.isEmpty()) {
            if (start == null) return "";
            return "(" + start.getLine() + ","
                    + start.getCharPositionInLine() + ")";
        }
        if (start == null) return "(" + file + ")";
        return "(" + file + ":" + start.getLine() + ","
                + start.getCharPositionInLine() + ")";
    }

    public static final Position ZERO = new Position("", null, null);
}
