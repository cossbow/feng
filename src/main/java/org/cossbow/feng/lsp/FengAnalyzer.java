package org.cossbow.feng.lsp;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class FengAnalyzer {

    private static final int DEBOUNCE_MS = 300;

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
