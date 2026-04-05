package org.cossbow.feng.analysis.layout;

import java.util.List;

/**
 * 布局结果
 */
public record StructureLayout(
        List<MemberLayout> members,
        long size, long align) {
}
