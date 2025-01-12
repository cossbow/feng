package org.cossbow.feng.ast;

public class SyntaxException extends RuntimeException {
    private final Position pos;

    public SyntaxException(String message, Position pos) {
        super(message);
        this.pos = pos;
    }

    public Position pos() {
        return pos;
    }
}
