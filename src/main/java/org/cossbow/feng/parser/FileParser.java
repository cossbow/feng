package org.cossbow.feng.parser;

import org.antlr.v4.runtime.*;
import org.cossbow.feng.ast.ParseSyntaxErrorMsg;
import org.cossbow.feng.ast.SourceFile;

import java.util.ArrayList;
import java.util.List;

public class FileParser {
    private final List<ParseSyntaxErrorMsg> errors = new ArrayList<>();

    private SourceFile root;

    public void parse(CharStream cs) {
        var lexer = new FengLexer(cs);
        var ts = new CommonTokenStream(lexer);
        var parser = new FengParser(ts);
        parser.addErrorListener(new ErrorCollector());
        var visitor = new FileParseVisitor();
        root = (SourceFile) visitor.visit(parser.feng());
    }

    public SourceFile root() {
        return root;
    }

    public List<ParseSyntaxErrorMsg> errors() {
        return errors;
    }

    private class ErrorCollector extends BaseErrorListener
            implements ANTLRErrorListener {

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol, int line,
                                int charPositionInLine, String msg,
                                RecognitionException e) {
            var er = new ParseSyntaxErrorMsg(line, charPositionInLine, msg);
            errors.add(er);
        }
    }
}
