package org.cossbow.feng.analysis.fmt;

/**
 * The argument of format
 */
public
class ArgumentSegment extends FormatSegment {
    /**
     * Index of argument
     */
    final int index; // 对应第几个参数

    public ArgumentSegment(int offset, int index) {
        super(offset);
        this.index = index;
    }

    public int index() {
        return index;
    }
}
