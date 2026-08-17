package org.cossbow.feng.analysis.meta;

import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.util.Optional;

import java.util.List;

/**
 * 一个具体类的运行时虚表布局（L1，不 emit）。
 *
 * <p>扁平前缀布局：slots 在父类 slots 基础上叠加（override 原位替换、新槽追加），
 * 子类 slots 结构上是父类的严格扩展，派发端 {@code ->m} 直达任意槽，无嵌套
 * {@code .base} 链。父类查询走运行时 {@code Feng$Meta.super}（由 {@link #parent()}
 * 提供），接口查询走 {@code Feng$Meta.ifaces}。
 */
public final class VTable {

    private final ClassDefinition def;
    private final IdentifierMap<VTableSlot> slots;
    private final List<IfaceVTable> ifaces;
    private final Optional<VTable> parent;

    public VTable(ClassDefinition def,
                  IdentifierMap<VTableSlot> slots,
                  List<IfaceVTable> ifaces,
                  Optional<VTable> parent) {
        this.def = def;
        this.slots = slots;
        this.ifaces = ifaces;
        this.parent = parent;
    }

    public ClassDefinition def() {
        return def;
    }

    /**
     * 槽位：方法名 → 槽，保持定义序（父类槽在前，override 原位替换）。
     */
    public IdentifierMap<VTableSlot> slots() {
        return slots;
    }

    /**
     * 接口虚表。
     */
    public List<IfaceVTable> ifaces() {
        return ifaces;
    }

    /**
     * 父类虚表（Object/builtin 根为空）——运行时 {@code .super} 字段的数据源。
     */
    public Optional<VTable> parent() {
        return parent;
    }

    //
    @Override
    public String toString() {
        return "meta:" + def.symbol();
    }
}
