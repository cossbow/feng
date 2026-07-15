package org.cossbow.feng.lsp;

import org.cossbow.feng.ast.Source;

import java.util.List;

public class AnalyzeResult {

    public static final AnalyzeResult EMPTY = new AnalyzeResult(null, List.of(), List.of());

    private final Source source;
    private final List<LspErrorCollector.SyntaxError> syntaxErrors;
    private final List<String> semanticErrors;

    public AnalyzeResult(Source source,
                         List<LspErrorCollector.SyntaxError> syntaxErrors,
                         List<String> semanticErrors) {
        this.source = source;
        this.syntaxErrors = syntaxErrors;
        this.semanticErrors = semanticErrors;
    }

    public Source source() {
        return source;
    }

    public List<LspErrorCollector.SyntaxError> syntaxErrors() {
        return syntaxErrors;
    }

    public List<String> semanticErrors() {
        return semanticErrors;
    }
}
