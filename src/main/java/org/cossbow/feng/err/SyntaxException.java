package org.cossbow.feng.err;

public class SyntaxException extends RuntimeException {
    public SyntaxException(String message, boolean trace) {
        super(message, null, true, trace);
    }
}
