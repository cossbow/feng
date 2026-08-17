package org.cossbow.feng.analysis.meta;

import org.cossbow.feng.ast.oop.InterfaceDefinition;

import java.util.List;

/**
 * 一个具体类的接口虚表：接口定义 + 槽位列表 + 接口 meta 符号。
 */
public final class IfaceVTable {

    private final InterfaceDefinition iface;
    private final List<IfaceSlot> slots;
    private final String metaSymbol;

    public IfaceVTable(InterfaceDefinition iface,
                       List<IfaceSlot> slots,
                       String metaSymbol) {
        this.iface = iface;
        this.slots = slots;
        this.metaSymbol = metaSymbol;
    }

    public InterfaceDefinition iface() {
        return iface;
    }

    public List<IfaceSlot> slots() {
        return slots;
    }

    /** Feng$meta_<ifaceKey>（泛型接口用 mangled 名）。 */
    public String metaSymbol() {
        return metaSymbol;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("i").append(iface.symbol())
                .append(" (").append(metaSymbol).append("): [");
        var first = true;
        for (var s : slots) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(s);
        }
        return sb.append(']').toString();
    }
}