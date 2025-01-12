package org.cossbow.feng.ast;

public record ParseSyntaxErrorMsg(int line, int charPositionInLine, String msg) {
}
