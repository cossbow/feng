package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.util.ErrorUtil;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 新 C 后端（纯代码生成器）。
 *
 * <p>单态化、匿名函数归一、方法降级（ClassMeta）、虚表布局（VTable）、释放分析
 * （ReleaserBuilder）全部由前置 pass 完成；本类只按有序结构单遍 dump C 文本，
 * 不持有任何 register-then-emit 的可变状态。六个模块化 writer 的编排门面：
 * TypeWriter（类型层）→ ExprWriter/StmtWriter（值层）→ FuncWriter（函数/方法层）
 * → MetaWriter（OOP 层）→ ReleaserWriter（释放/入口层）。
 */
public class CGenerator implements Generator {

    private final WriterContext context;
    private final TopWriter top;

    public CGenerator(AnalyseSymbolTable table,
                      Appendable out,
                      boolean header,
                      boolean debug) {
        this.context = new WriterContext(table, out, header, debug);
        this.top = new TopWriter(context);
    }

    // ---- Factory ----

    /**
     * 随构建产物一起拷贝的 C 运行时（与旧后端共用同一套 ABI）。
     */
    static final String[] BaseDeps = {"Header.h", "builtin.h", "builtin.c"};
    static final String mainFile = "c/Main.c";

    public static final Factory FACTORY = new Factory() {
        @Override
        public Generator create(AnalyseSymbolTable ast, Appendable out,
                                boolean header, boolean debug) {
            return new CGenerator(ast, out, header, debug);
        }

        @Override
        public String extension() {
            return ".c";
        }

        @Override
        public void copyBaseHeader(Path dir) {
            for (String s : BaseDeps) {
                try (var is = getResource("c/" + s)) {
                    var target = dir.resolve(s);
                    Files.deleteIfExists(target);
                    Files.copy(is, target);
                } catch (IOException e) {
                    throw ErrorUtil.sneaky(e);
                }
            }
        }

        @Override
        public String compiler() {
            return "clang";
        }

        @Override
        public String version() {
            return "c11";
        }
    };

    private static InputStream getResource(String res) {
        var cl = Thread.currentThread().getContextClassLoader();
        return new BufferedInputStream(Objects.requireNonNull(
                cl.getResourceAsStream(res)));
    }

    @Override
    public void write() {
        top.write();
    }
}
