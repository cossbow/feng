package org.cossbow.feng.err;

public class SemanticException extends RuntimeException {
    public SemanticException(String message, boolean trace) {
        super(message, null, true, trace);
    }
}
