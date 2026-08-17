package org.cossbow.feng.analysis.meta;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.oop.InterfaceMethod;

/**
 * 接口虚表中的一个槽位：接口方法 → 实现类方法符号 + 签名。
 */
public final class IfaceSlot {

    private final InterfaceMethod method;
    private final VTableSlot impl;

    public IfaceSlot(InterfaceMethod method,
                     VTableSlot impl) {
        this.method = method;
        this.impl = impl;
    }

    public InterfaceMethod method() {
        return method;
    }

    public Identifier name() {
        return method.name();
    }

    public VTableSlot impl() {
        return impl;
    }

    public String symbol() {
        return impl.symbol();
    }

    @Override
    public String toString() {
        return method.name().value() + " → " + impl.symbol();
    }
}