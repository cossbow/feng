package org.cossbow.feng.parser;

import org.antlr.v4.runtime.*;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.util.ErrorUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SourceParser {
    private final Charset charset;
    private final ParseSymbolTable table;

    public SourceParser(Charset charset, ParseSymbolTable table) {
        this.charset = charset;
        this.table = table;
    }

    public Source parse(String file, CharStream cs) {
        var lexer = new FengLexer(cs);
        var ts = new CommonTokenStream(lexer);
        var parser = new FengParser(ts);
        var ec = new ErrorCollector();
        parser.addErrorListener(ec);
        var visitor = new SourceParseVisitor(file, charset, table);
        return (Source) visitor.visit(parser.source());
    }

    public Source parse(Path file) {
        try {
            return parse(file.toString(),
                    CharStreams.fromPath(file, charset));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static class ErrorCollector extends BaseErrorListener
            implements ANTLRErrorListener {
        private final List<SyntaxErrorMsg> errors = new ArrayList<>();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol, int line,
                                int charPositionInLine, String msg,
                                RecognitionException e) {
            ErrorUtil.syntax("parse error at (%d:%d): %s: \n",
                    line, charPositionInLine, msg);
        }
    }
}
