package org.cossbow.feng.ast;

import org.cossbow.feng.parser.SyntaxErrorMsg;

import java.util.List;

public class SyntaxException extends RuntimeException {
    private final List<SyntaxErrorMsg> errors;

    public SyntaxException(List<SyntaxErrorMsg> errors) {
        super(errors.toString());
        this.errors = errors;
    }

    public List<SyntaxErrorMsg> errors() {
        return errors;
    }

}
