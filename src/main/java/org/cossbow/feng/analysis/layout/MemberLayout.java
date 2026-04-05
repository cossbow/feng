package org.cossbow.feng.analysis.layout;

/**
 * 计算出的字段布局信息
 *
 * @param offset    字节偏移
 * @param size      字节大小
 * @param bitOffset 位偏移（仅位域有效）
 * @param bitWidth  位宽（仅位域有效）
 */
public record MemberLayout(
        long offset, long size, long bitOffset,
        long bitWidth, boolean isBitField) {
    public MemberLayout(long offset, long size) {
        this(offset, size, 0, 0, false);
    }
}
