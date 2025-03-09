package org.cossbow.feng.parser;

public record SyntaxErrorMsg(int line, int charPositionInLine, String msg) {

    @Override
    public String toString() {
        return "At position %d:%d: syntax error: %s".formatted(line, charPositionInLine, msg);
    }

}
