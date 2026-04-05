package org.cossbow.feng.parser;

import org.antlr.v4.runtime.*;
import org.cossbow.feng.ast.Source;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.err.SyntaxException;
import org.cossbow.feng.util.Optional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

public class SourceParser {
    private final Optional<ModulePath> module;
    private final Charset charset;
    private final boolean metadata;

    public SourceParser(Optional<ModulePath> module,
                        Charset charset,
                        boolean metadata) {
        this.module = module;
        this.charset = charset;
        this.metadata = metadata;
    }

    public SourceParser(ModulePath module,
                        Charset charset,
                        boolean metadata) {
        this(Optional.of(module), charset, metadata);
    }

    public SourceParser(ModulePath module,
                        Charset charset) {
        this(Optional.of(module), charset, false);
    }

    public SourceParser(Charset charset) {
        this(Optional.empty(), charset, false);
    }

    public Source parse(String file, CharStream cs) {
        var lexer = new FengLexer(cs);
        var ts = new CommonTokenStream(lexer);
        var parser = new FengParser(ts);
        var ec = new ErrorCollector(file);
        parser.addErrorListener(ec);
        var visitor = new SourceParseVisitor(file, module,
                charset, new ParseSymbolTable(module), metadata);
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
        private final String file;

        ErrorCollector(String file) {
            this.file = file;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol, int line,
                                int charPositionInLine, String msg,
                                RecognitionException e) {
            throw new SyntaxException("parse error at %s(%d:%d): \n%s"
                    .formatted(file, line, charPositionInLine, msg));
        }
    }
}
