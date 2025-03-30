package org.cossbow.feng.parser;

import java.util.List;

public class ParseException extends RuntimeException {
    private final List<SyntaxErrorMsg> errors;

    public ParseException(List<SyntaxErrorMsg> errors) {
        super(errors.toString());
        this.errors = errors;
    }

    public List<SyntaxErrorMsg> errors() {
        return errors;
    }

}
