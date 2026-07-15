package org.cossbow.feng.lsp;

import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FengDocumentStore {

    private final Map<String, FengDocument> documents = new ConcurrentHashMap<>();

    public void open(String uri, String text, int version) {
        documents.put(uri, new FengDocument(uri, text, version));
    }

    public void close(String uri) {
        documents.remove(uri);
    }

    public void update(String uri, int version, List<TextDocumentContentChangeEvent> changes) {
        var doc = documents.get(uri);
        if (doc != null) {
            doc.applyChanges(changes, version);
        }
    }

    public FengDocument get(String uri) {
        return documents.get(uri);
    }

    public String getText(String uri) {
        var doc = documents.get(uri);
        return doc != null ? doc.text() : null;
    }
}
