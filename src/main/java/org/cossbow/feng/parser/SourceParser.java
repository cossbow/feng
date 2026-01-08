package org.cossbow.feng.parser;

import org.antlr.v4.runtime.*;
import org.cossbow.feng.ast.Source;

import java.util.ArrayList;
import java.util.List;

public class SourceParser {

    private final String file;
    private final GlobalSymbolTable gst;

    public SourceParser(String file, GlobalSymbolTable gst) {
        this.file = file;
        this.gst = gst;
    }

    public ParseResult parse(CharStream cs) {
        var lexer = new FengLexer(cs);
        var ts = new CommonTokenStream(lexer);
        var parser = new FengParser(ts);
        var ec = new ErrorCollector();
        parser.addErrorListener(ec);
        var visitor = new SourceParseVisitor(file, gst);
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
            var er = new SyntaxErrorMsg(line, charPositionInLine, msg);
            errors.add(er);
        }
    }
}
