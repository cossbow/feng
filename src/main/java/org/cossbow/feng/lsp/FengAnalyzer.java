package org.cossbow.feng.lsp;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.cossbow.feng.analysis.SemanticAnalyzer;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.parser.FengLexer;
import org.cossbow.feng.parser.FengParser;
import org.cossbow.feng.parser.ParseSymbolTable;
import org.cossbow.feng.parser.SourceParseVisitor;
import org.cossbow.feng.util.DedupCache;
import org.cossbow.feng.util.Optional;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class FengAnalyzer {

    private static final int DEBOUNCE_MS = 300;

    // ---- Semantic tokens: legend (shared with FengLanguageServer) ----

    public static final List<String> TOKEN_TYPES = List.of(
            "keyword", "type", "function", "variable",
            "string", "number", "comment");

    public static final List<String> TOKEN_MODIFIERS = List.of("declaration");

    private static final int T_KEYWORD = 0;
    private static final int T_TYPE = 1;
    private static final int T_FUNCTION = 2;
    private static final int T_VARIABLE = 3;
    private static final int T_STRING = 4;
    private static final int T_NUMBER = 5;
    private static final int T_COMMENT = 6;

    private static final int MOD_DECLARATION = 1;

    private static final Set<Integer> KEYWORD_TOKENS = Set.of(
            FengLexer.FENG1, FengLexer.FENG2, FengLexer.FENG3,
            FengLexer.EXPORT, FengLexer.IMPORT,
            FengLexer.STRUCT, FengLexer.UNION, FengLexer.ENUM,
            FengLexer.ATTRIBUTE, FengLexer.INTERFACE, FengLexer.CLASS,
            FengLexer.FUNC, FengLexer.MACRO, FengLexer.CONST,
            FengLexer.VAR, FengLexer.LET, FengLexer.NEW, FengLexer.SIZEOF,
            FengLexer.RETURN, FengLexer.IF, FengLexer.ELSE, FengLexer.FOR,
            FengLexer.CONTINUE, FengLexer.BREAK, FengLexer.SWITCH, FengLexer.CASE,
            FengLexer.DEFAULT, FengLexer.THROW, FengLexer.TRY, FengLexer.CATCH,
            FengLexer.FINAL, FengLexer.STATIC, FengLexer.ASSERT,
            FengLexer.THIS, FengLexer.SUPER,
            FengLexer.BoolLiteral, FengLexer.NilLiteral);

    private static final Set<Integer> NUMBER_TOKENS = Set.of(
            FengLexer.FloatLiteral, FengLexer.DecimalInteger,
            FengLexer.HexInteger, FengLexer.OctalInteger, FengLexer.BinaryInteger);

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r, "feng-lsp-analyzer");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final Map<String, AnalyzeResult> cache = new ConcurrentHashMap<>();
    private final FengDocumentStore documents;

    public FengAnalyzer(FengDocumentStore documents) {
        this.documents = documents;
    }

    // ---- Analysis ----

    public void requestAnalysis(String uri, LanguageClient client) {
        pending.compute(uri, (u, prev) -> {
            if (prev != null) prev.cancel(false);
            return scheduler.schedule(
                    () -> analyze(uri, client),
                    DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        });
    }

    public AnalyzeResult analyze(String uri, LanguageClient client) {
        var doc = documents.get(uri);
        if (doc == null) return AnalyzeResult.EMPTY;

        var text = doc.text();
        var fileName = LspUtil.extractFileName(uri);

        // Parse with error-tolerant ANTLR4 setup
        var cs = CharStreams.fromString(text);
        var lexer = new FengLexer(cs);
        var tokenStream = new CommonTokenStream(lexer);
        var parser = new FengParser(tokenStream);

        var errorCollector = new LspErrorCollector(fileName);
        parser.removeErrorListeners();
        parser.addErrorListener(errorCollector);

        var parseTree = parser.source();

        // Build AST via SourceParseVisitor (catch internal syntax errors)
        Source source = null;
        try {
            var visitor = new SourceParseVisitor(
                    fileName,
                    Optional.empty(),
                    Charset.defaultCharset(),
                    new ParseSymbolTable(Optional.empty(), new DedupCache<>()),
                    false);
            source = (Source) visitor.visit(parseTree);
        } catch (Exception e) {
            // Visitor may throw on malformed input; keep what we have
        }

        // Semantic analysis (catch semantic errors)
        var semanticErrors = new ArrayList<String>();
        if (source != null) {
            var analyzer = new SemanticAnalyzer(source.table());
            try {
                analyzer.analyse();
            } catch (Exception e) {
                semanticErrors.add(e.getMessage());
            }
            semanticErrors.addAll(analyzer.errors());
        }

        var result = new AnalyzeResult(source, errorCollector.errors(), semanticErrors);
        cache.put(uri, result);

        publishDiagnostics(client, uri, result);
        return result;
    }

    public AnalyzeResult getCached(String uri) {
        return cache.get(uri);
    }

    public void remove(String uri) {
        cache.remove(uri);
        var prev = pending.remove(uri);
        if (prev != null) prev.cancel(false);
    }

    // ---- Diagnostic publishing ----

    private void publishDiagnostics(LanguageClient client, String uri, AnalyzeResult result) {
        if (client == null) return; // MCP mode: no LSP client attached
        var diagnostics = new ArrayList<Diagnostic>();
        for (var err : result.syntaxErrors()) {
            diagnostics.add(new Diagnostic(
                    LspUtil.toRange(err.line(), err.charPositionInLine(),
                            err.token() != null ? err.token().getText().length() : 1),
                    err.message(),
                    DiagnosticSeverity.Error,
                    "feng"));
        }
        for (var msg : result.semanticErrors()) {
            diagnostics.add(new Diagnostic(
                    new Range(new org.eclipse.lsp4j.Position(0, 0),
                            new org.eclipse.lsp4j.Position(0, 0)),
                    msg,
                    DiagnosticSeverity.Error,
                    "feng"));
        }
        client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnostics));
    }

    // ---- Phase 2: documentSymbol ----

    public List<Either<SymbolInformation, DocumentSymbol>> documentSymbol(String uri) {
        var result = cache.get(uri);
        if (result == null || result.source() == null) return List.of();

        var table = result.source().table();
        var symbols = new ArrayList<Either<SymbolInformation, DocumentSymbol>>();

        for (var type : table.types.values()) {
            symbols.add(Either.forRight(LspUtil.toDocumentSymbol(type)));
        }
        for (var func : table.functions.values()) {
            symbols.add(Either.forRight(LspUtil.toDocumentSymbol(func)));
        }
        for (var v : table.variables.values()) {
            symbols.add(Either.forRight(LspUtil.toDocumentSymbol(v)));
        }
        return symbols;
    }

    // ---- Phase 2: hover ----

    public Hover hover(String uri, org.eclipse.lsp4j.Position position) {
        var result = cache.get(uri);
        if (result == null || result.source() == null) return null;

        var doc = documents.get(uri);
        if (doc == null) return null;

        var identifier = LspUtil.identifierAt(doc.text(), position);
        if (identifier == null) return null;

        var table = result.source().table();
        var id = new Identifier(identifier);

        // Lookup type
        var type = table.types.get(id);
        if (type != null) {
            return new Hover(
                    new MarkupContent(MarkupKind.MARKDOWN,
                            "```feng\n" + LspUtil.formatTypeDefinition(type) + "\n```"),
                    LspUtil.toRange(type.pos()));
        }

        // Lookup function
        var func = table.functions.get(id);
        if (func != null) {
            return new Hover(
                    new MarkupContent(MarkupKind.MARKDOWN,
                            "```feng\n" + LspUtil.formatFunctionSignature(func) + "\n```"),
                    LspUtil.toRange(func.pos()));
        }

        // Lookup variable
        var v = table.variables.get(id);
        if (v != null) {
            return new Hover(
                    new MarkupContent(MarkupKind.MARKDOWN,
                            "```feng\n" + LspUtil.formatVariableSignature(v) + "\n```"),
                    LspUtil.toRange(v.pos()));
        }

        return null;
    }

    // ---- Phase 2: definition ----

    public List<Location> definition(String uri, org.eclipse.lsp4j.Position position) {
        var result = cache.get(uri);
        if (result == null || result.source() == null) return List.of();

        var doc = documents.get(uri);
        if (doc == null) return List.of();

        var identifier = LspUtil.identifierAt(doc.text(), position);
        if (identifier == null) return List.of();

        var table = result.source().table();
        var id = new Identifier(identifier);

        // Lookup type definition
        var type = table.types.get(id);
        if (type != null && type.pos() != org.cossbow.feng.ast.Position.ZERO) {
            return List.of(toLocation(type.pos()));
        }

        // Lookup function definition
        var func = table.functions.get(id);
        if (func != null && func.pos() != org.cossbow.feng.ast.Position.ZERO) {
            return List.of(toLocation(func.pos()));
        }

        // Lookup variable definition
        var v = table.variables.get(id);
        if (v != null && v.pos() != org.cossbow.feng.ast.Position.ZERO) {
            return List.of(toLocation(v.pos()));
        }

        return List.of();
    }

    private Location toLocation(org.cossbow.feng.ast.Position pos) {
        var range = LspUtil.toRange(pos);
        var file = pos.file();
        var locationUri = (!file.isEmpty()) ? "file://" + file : "";
        return new Location(locationUri, range);
    }

    // ---- Phase 2: completion ----

    private static final List<String> KEYWORDS = List.of(
            "import", "export", "struct", "union", "enum", "attribute",
            "interface", "class", "func", "macro", "const", "var", "let",
            "new", "sizeof", "return", "if", "else", "for", "continue",
            "break", "switch", "case", "default", "throw", "try",
            "catch", "final", "static", "assert", "this", "super", "nil",
            "true", "false");

    private static final List<String> BUILTIN_TYPES = List.of(
            "int", "uint", "long", "ulong", "float", "double", "byte",
            "bool", "size");

    public CompletionList completion(String uri, org.eclipse.lsp4j.Position position) {
        var items = new ArrayList<CompletionItem>();

        // Keywords
        for (var kw : KEYWORDS) {
            items.add(keywordItem(kw));
        }

        // Builtin types
        for (var bt : BUILTIN_TYPES) {
            items.add(typeItem(bt));
        }

        // Scope symbols from current file
        var result = cache.get(uri);
        if (result != null && result.source() != null) {
            var table = result.source().table();

            for (var type : table.types.values()) {
                items.add(typeItem(type.symbol().name().value()));
            }
            for (var func : table.functions.values()) {
                items.add(funcItem(func));
            }
            for (var v : table.variables.values()) {
                items.add(varItem(v));
            }
        }

        return new CompletionList(items);
    }

    // ---- Semantic tokens ----

    /**
     * Builds the flat integer list for textDocument/semanticTokens (full).
     * Encoding: [deltaLine, deltaStartChar, length, tokenType, tokenModifiers] per token.
     */
    public List<Integer> semanticTokens(String uri) {
        var doc = documents.get(uri);
        if (doc == null) return List.of();
        var text = doc.text();

        // Best-effort symbol classification from the latest parse.
        ParseSymbolTable table = null;
        var result = cache.get(uri);
        if (result != null && result.source() != null) {
            table = result.source().table();
        }

        // Declaration positions (for the "declaration" modifier).
        var decls = new HashSet<String>();
        if (table != null) {
            for (var t : table.types.values()) mark(decls, t.symbol().name().pos());
            for (var f : table.functions.values()) mark(decls, f.symbol().name().pos());
            for (var v : table.variables.values()) mark(decls, v.symbol().name().pos());
        }

        var lexer = new FengLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        var tokens = lexer.getAllTokens();

        var data = new ArrayList<Integer>();
        int prevLine = 0, prevChar = 0;
        for (var token : tokens) {
            int type = classify(token, table);
            if (type < 0) continue;

            int modifiers = (token.getType() == FengLexer.Identifier
                    && decls.contains(token.getLine() + ":" + token.getCharPositionInLine()))
                    ? MOD_DECLARATION : 0;

            // A token (e.g. a block comment) may span lines; emit one entry per line.
            var value = token.getText();
            int line = token.getLine() - 1;
            int charPos = token.getCharPositionInLine();
            int from = 0;
            while (from < value.length()) {
                int nl = value.indexOf('\n', from);
                int length = (nl < 0 ? value.length() : nl) - from;
                if (length > 0 && value.charAt(from + length - 1) == '\r') length--;

                if (length > 0) {
                    int deltaLine = line - prevLine;
                    int deltaChar = (deltaLine == 0) ? charPos - prevChar : charPos;
                    data.add(deltaLine);
                    data.add(deltaChar);
                    data.add(length);
                    data.add(type);
                    data.add(modifiers);
                    prevLine = line;
                    prevChar = charPos;
                }
                if (nl < 0) break;
                from = nl + 1;
                line++;
                charPos = 0;
            }
        }
        return data;
    }

    private static int classify(Token token, ParseSymbolTable table) {
        int t = token.getType();
        if (t == FengLexer.StringLiteral) return T_STRING;
        if (NUMBER_TOKENS.contains(t)) return T_NUMBER;
        if (t == FengLexer.COMMENT || t == FengLexer.LINE_COMMENT) return T_COMMENT;
        if (KEYWORD_TOKENS.contains(t)) return T_KEYWORD;
        if (t == FengLexer.Identifier) {
            if (table != null) {
                var id = new Identifier(token.getText());
                if (table.findType(id).has()) return T_TYPE;
                if (table.findFunc(id).has()) return T_FUNCTION;
            }
            return T_VARIABLE;
        }
        return -1; // operators / separators / punctuation: not highlighted via LSP
    }

    private static void mark(Set<String> decls, org.cossbow.feng.ast.Position pos) {
        if (pos == null || pos.start() == null) return;
        decls.add(pos.start().getLine() + ":" + pos.start().getCharPositionInLine());
    }

    private static CompletionItem keywordItem(String keyword) {
        var item = new CompletionItem(keyword);
        item.setKind(CompletionItemKind.Keyword);
        return item;
    }

    private static CompletionItem typeItem(String name) {
        var item = new CompletionItem(name);
        item.setKind(CompletionItemKind.Class);
        return item;
    }

    private static CompletionItem funcItem(FunctionDefinition func) {
        var item = new CompletionItem(func.symbol().name().value());
        item.setKind(CompletionItemKind.Function);
        item.setDetail(LspUtil.formatFunctionSignature(func));
        return item;
    }

    private static CompletionItem varItem(Variable v) {
        var item = new CompletionItem(v.name().value());
        item.setKind(v.isConst() ? CompletionItemKind.Constant : CompletionItemKind.Variable);
        return item;
    }
}
