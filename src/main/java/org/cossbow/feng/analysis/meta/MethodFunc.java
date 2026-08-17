package org.cossbow.feng.analysis.meta;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.proc.Procedure;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Optional;

/**
 * 一个方法降级后的完整函数描述符。
 * 后端用它生成函数声明、定义与调用，不再接触 ClassMethod。
 *
 * <p>{@code prototype} 首参固定为 {@code SelfParameter}（self，类型 =
 * {@code master.def()} 的虚引用 {@code X*}），由 ClassMetadata.methodFunc /
 * ReleaserBuilder.buildDestroy 统一注入；后端不再对 self 走例外分支。
 */
public final class MethodFunc {

    private final Identifier name;
    private final String symbol;
    private final Prototype prototype;
    private final Optional<Procedure> body;
    private final ClassMeta master;
    private final boolean dynamic;

    public MethodFunc(Identifier name,
                      String symbol,
                      Prototype prototype,
                      Optional<Procedure> body,
                      ClassMeta master,
                      boolean dynamic) {
        this.name = name;
        this.symbol = symbol;
        this.prototype = prototype;
        this.body = body;
        this.master = master;
        this.dynamic = dynamic;
    }

    public Identifier name() {
        return name;
    }

    /**
     * C 函数符号，如 {@code pkg$Foo$bar}。
     */
    public String symbol() {
        return symbol;
    }

    /**
     * 原始方法原型（不含 {@code self} 首参）。
     * 后端写入时在前面补 {@code void* self}。
     */
    public Prototype prototype() {
        return prototype;
    }

    /**
     * 函数体（无实现时为空，如 metadata 中的方法声明）。
     */
    public Optional<Procedure> body() {
        return body;
    }

    public ClassMeta master() {
        return master;
    }

    public boolean dynamic() {
        return dynamic;
    }

    //
    @Override
    public String toString() {
        return symbol + prototype;
    }
}