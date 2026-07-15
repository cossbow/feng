package org.cossbow.feng.lsp;

import org.antlr.v4.runtime.*;

import java.util.ArrayList;
import java.util.List;

/**
 * ANTLR4 error listener that collects syntax errors instead of throwing.
 * Used by LSP to tolerate partial/broken source code and still provide diagnostics.
 */
public class LspErrorCollector extends BaseErrorListener {

    private final List<SyntaxError> errors = new ArrayList<>();
    private final String file;

    public LspErrorCollector(String file) {
        this.file = file;
    }

    public List<SyntaxError> errors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer,
                            Object offendingSymbol,
                            int line, int charPositionInLine,
                            String msg,
                            RecognitionException e) {
        errors.add(new SyntaxError(file, line, charPositionInLine, msg,
                offendingSymbol instanceof Token t ? t : null));
    }

    public record SyntaxError(String file, int line, int charPositionInLine,
                              String message, Token token) {
    }
}
