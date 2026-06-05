package org.cossbow.feng.analysis.fmt;

abstract
public class FormatSegment {
    /**
     * Offset in source
     */
    private final int offset;

    public FormatSegment(int offset) {
        this.offset = offset;
    }

    public int offset() {
        return offset;
    }
}
