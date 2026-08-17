package org.cossbow.feng.coder;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.util.ErrorUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 共享 C 文本写入基类（NC 后端各模块 writer 的地基）。
 *
 * <p>这是**同步、单遍、立即写入**的文本底座：{@code write(...)} 立刻追加到
 * {@link Appendable}，没有任何「先注册后发射」的延迟状态。与已废弃的 lazy/emit
 * 反模式无关——后者指的是 {@code List<Runnable>} / {@code Set} 那种「先收集后 flush」的
 * 做法。命名用 {@code CWriter} 而非 Emitter，正是为了与那个反模式区分开。
 *
 * <p>只负责「把内容写进去」与最基本的 C 文本排版（缩进、换行、语句结尾、注释），
 * 以及 Fēng 类型 / 符号 → C 名字的通用公式；不含任何语义分析。
 *
 * <p>自型泛型 {@code Self} 让子类（{@code NCGenerator} 等）链式调用时仍返回子类类型。
 */
public abstract class CWriter<Self extends CWriter<Self>> {
    protected WriterContext context;
    protected Appendable out;

    protected CWriter(WriterContext context) {
        this.context = context;
        this.out = context.out;
    }

    @SuppressWarnings("unchecked")
    private Self self() {
        return (Self) this;
    }

    // ---- primitive name mapping（与旧 CGenerator.PrimitiveName 一致） ----

    /**
     * Fēng 原始类型 → C 类型名（INT → "Int", FLOAT64 → "Float64"）。
     */
    public static final Map<Primitive, String> PrimitiveName =
            Arrays.stream(Primitive.values())
                    .collect(Collectors.toMap(Function.identity(),
                            p -> {
                                var s = p.code;
                                return Character.toUpperCase(s.charAt(0)) + s.substring(1);
                            }));

    // ---- basic output（同步立即写） ----

    protected Self write(char c) {
        try {
            out.append(c);
        } catch (IOException e) {
            ErrorUtil.io(e);
        }
        return self();
    }

    protected Self write(CharSequence cs) {
        try {
            out.append(cs);
        } catch (IOException e) {
            ErrorUtil.io(e);
        }
        return self();
    }

    protected Self write(int b) {
        return write(Integer.toString(b));
    }

    protected Self write(long b) {
        return write(Long.toString(b));
    }

    protected Self write(Identifier name) {
        if (!name.unnamed()) write('$');
        return write(name.value());
    }

    protected Self write(Primitive p) {
        return write(PrimitiveName.get(p));
    }

    /**
     * 符号 → C 标识符：{@code [module$]Name}。
     */
    protected Self write(Symbol s) {
        s.module().use(mp -> write(mp.toString()));
        return write(s.name());
    }

    /**
     * {@link #write(Symbol)} 的字符串形式，供拼接名字用（与 {@code ClassMeta.symbolName} 一致）。
     */
    protected String symbolName(Symbol s) {
        var sb = new StringBuilder();
        s.module().use(mp -> sb.append(mp.toString()));
        sb.append('$').append(s.name().value());
        return sb.toString();
    }

    // ---- layout helpers ----

    protected Self indent() {
        context.dent.inc();
        return newLine();
    }

    protected Self dedent() {
        context.dent.dec();
        return newLine();
    }

    protected Self newLine() {
        write('\n');
        for (int i = 0; i < context.dent.value(); i++) {
            write('\t');
        }
        return self();
    }

    protected Self endStmt() {
        return write(";").newLine();
    }

    protected void writeComment(String text) {
        write("// ").write(text).newLine();
    }
}
