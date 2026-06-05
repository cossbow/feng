package org.cossbow.feng.analysis.fmt;

import org.cossbow.feng.ast.lit.StringLiteral;

/**
 * The text part of format
 */
public
class TextSegment extends FormatSegment {
    private final StringLiteral text;

    public TextSegment(int offset, StringLiteral text) {
        super(offset);
        this.text = text;
    }

    public StringLiteral text() {
        return text;
    }
}
