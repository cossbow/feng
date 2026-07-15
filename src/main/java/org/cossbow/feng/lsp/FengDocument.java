package org.cossbow.feng.lsp;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

import java.util.List;

public class FengDocument {

    private final String uri;
    private String text;
    private int version;

    public FengDocument(String uri, String text, int version) {
        this.uri = uri;
        this.text = text;
        this.version = version;
    }

    public String uri() {
        return uri;
    }

    public String text() {
        return text;
    }

    public int version() {
        return version;
    }

    public void applyChanges(List<TextDocumentContentChangeEvent> changes, int version) {
        this.version = version;
        for (var change : changes) {
            if (change.getRange() == null) {
                // Full update
                this.text = change.getText();
            } else {
                // Incremental update
                this.text = applyRangeChange(this.text, change.getRange(), change.getText());
            }
        }
    }

    private static String applyRangeChange(String text, Range range, String newText) {
        var lines = text.split("\n", -1);
        int startOffset = lineCharToOffset(lines, range.getStart().getLine(), range.getStart().getCharacter());
        int endOffset = lineCharToOffset(lines, range.getEnd().getLine(), range.getEnd().getCharacter());
        return text.substring(0, startOffset) + newText + text.substring(endOffset);
    }

    private static int lineCharToOffset(String[] lines, int line, int character) {
        int offset = 0;
        for (int i = 0; i < line && i < lines.length; i++) {
            offset += lines[i].length() + 1; // +1 for '\n'
        }
        return offset + character;
    }
}
