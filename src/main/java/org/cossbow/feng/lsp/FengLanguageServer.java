package org.cossbow.feng.lsp;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FengLanguageServer implements LanguageServer, TextDocumentService, WorkspaceService {

    private LanguageClient client;
    private final FengDocumentStore documents = new FengDocumentStore();
    private final FengAnalyzer analyzer = new FengAnalyzer(documents);

    // ---- LanguageServer ----

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        var caps = new ServerCapabilities();
        caps.setTextDocumentSync(TextDocumentSyncKind.Incremental);

        // Completion
        var completionOptions = new CompletionOptions(
                false, List.of(".", "$", "`", "(", "#", "*"));
        caps.setCompletionProvider(completionOptions);

        caps.setHoverProvider(true);
        caps.setDefinitionProvider(true);
        caps.setDocumentSymbolProvider(true);

        return CompletableFuture.completedFuture(new InitializeResult(caps));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.exit(0);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return this;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return this;
    }

    public void connect(LanguageClient client) {
        this.client = client;
    }

    // ---- TextDocumentService ----

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var td = params.getTextDocument();
        documents.open(td.getUri(), td.getText(), td.getVersion());
        analyzer.analyze(td.getUri(), client);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        var td = params.getTextDocument();
        documents.update(td.getUri(), td.getVersion(),
                params.getContentChanges());
        analyzer.requestAnalysis(td.getUri(), client);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        var uri = params.getTextDocument().getUri();
        documents.close(uri);
        analyzer.remove(uri);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // Analysis is driven by didChange
    }

    // ---- Completion ----

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
            CompletionParams position) {
        return CompletableFuture.completedFuture(
                Either.forRight(analyzer.completion(
                        position.getTextDocument().getUri(),
                        position.getPosition())));
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem params) {
        return CompletableFuture.completedFuture(params);
    }

    // ---- Hover ----

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFuture.completedFuture(
                analyzer.hover(params.getTextDocument().getUri(),
                        params.getPosition()));
    }

    // ---- Definition ----

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
    definition(DefinitionParams params) {
        var locations = analyzer.definition(
                params.getTextDocument().getUri(),
                params.getPosition());
        return CompletableFuture.completedFuture(Either.forLeft(locations));
    }

    // ---- Document Symbol ----

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>>
    documentSymbol(DocumentSymbolParams params) {
        return CompletableFuture.completedFuture(
                analyzer.documentSymbol(params.getTextDocument().getUri()));
    }

    // ---- WorkspaceService ----

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // No action
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        // Could re-analyze affected files in the future
    }
}
