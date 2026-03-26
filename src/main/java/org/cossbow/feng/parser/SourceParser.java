package org.cossbow.feng.parser;

import org.antlr.v4.runtime.*;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.util.ErrorUtil;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class SourceParser {

    private final String file;
    private final Charset charset;
    private final ParseSymbolTable tab;

    public SourceParser(String file, Charset charset, ParseSymbolTable tab) {
        this.file = file;
        this.charset = charset;
        this.tab = tab;
    }

    public ParseResult parse(CharStream cs) {
        var lexer = new FengLexer(cs);
        var ts = new CommonTokenStream(lexer);
        var parser = new FengParser(ts);
        var ec = new ErrorCollector();
        parser.addErrorListener(ec);
        var visitor = new SourceParseVisitor(file, charset, tab);
        var root = (Source) visitor.visit(parser.source());
        return new ParseResult(root, ec.errors);
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
