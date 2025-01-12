package org.cossbow.feng.ast;

import org.antlr.v4.runtime.Token;

public record Position(Token start, Token stop) {

    public static final Position ZERO = new Position(null, null);
}
