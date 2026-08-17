package org.cossbow.feng.analysis.meta;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.proc.Prototype;

/**
 * vtable 中的一个槽位：方法名 → 函数符号 + 实现类 + 签名。
 */
public final class VTableSlot {

    private final MethodFunc method;

    public VTableSlot(MethodFunc method) {
        this.method = method;
    }

    public MethodFunc method() {
        return method;
    }

    /**
     * 槽名（free 是 macro id）。
     */
    public Identifier name() {
        return method.name();
    }

    /**
     * 函数符号。
     */
    public String symbol() {
        return method.symbol();
    }

    /**
     * 真正提供实现的类（覆盖时 ≠ 当前类）。
     */
    public ClassDefinition owner() {
        return method.master().def();
    }

    /**
     * 函数指针签名（首参 self）。
     */
    public Prototype signature() {
        return method.prototype();
    }

    @Override
    public String toString() {
        return name() + " → " + symbol();
    }
}