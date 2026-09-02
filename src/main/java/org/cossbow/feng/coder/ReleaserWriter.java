package org.cossbow.feng.coder;

import org.cossbow.feng.ast.GlobalVariable;
import org.cossbow.feng.ast.Mangle;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.dag.DAGGraph;

import java.io.*;
import java.util.Objects;

/**
 * 释放/入口层 writer（阶段 7）：dump cleanups + classMetas.destroy() 函数体 +
 * 全局常量/变量定义 + 运行时初始化（constructor/destructor）+ test runner（{@code -T}）/ main。
 *
 * <p>纯发射器：cleanup / destroy 的 AST 已由 ReleaserBuilder 前置生成（body 内槽位变量
 * {@code p_<id>} / {@code self_<id>}，参数名从 body 实际引用提取）；全局变量按 constVars
 * 声明序 + dagVars 依赖序发射；本类不持有任何 register-then-emit 的可变状态。
 *
 * <p>字符串池（{@code Feng$constString_<id>}）由 {@link TypeWriter#literalStringCache}
 * 发射，本层不重复处理。实施指导：{@code docs/releaser-writer.md}。
 */
public class ReleaserWriter extends CWriter<ReleaserWriter> {

    public ReleaserWriter(WriterContext context) {
        super(context);
    }

    // ===================================================================
    //  全局常量 / 变量 [S]
    // ===================================================================

    /**
     * 全局常量定义（constVars 声明序）。
     */
    public void globalConsts() {
        declareGlobalVar(context.table.constVars);
    }

    /**
     * 全局变量定义（dagVars 依赖序）。
     */
    public void globalVars() {
        declareGlobalVar(context.table.dagVars);
    }

    /**
     * 全局常量/变量定义（先静态前向声明非导出变量，再按序定义）。
     */
    private void declareGlobalVar(java.util.List<GlobalVariable> vars) {
        writeComment("global variable");
        for (var v : vars) {
            if (v.export()) continue;
            write("static ");
            context.types.write(v.type().must()).write(' ');
            varName(v).endStmt();
        }
        for (var v : vars) write(v);
        newLine();
    }

    private void declareGlobalVar(DAGGraph<GlobalVariable> vars) {
        writeComment("global variable");
        for (var v : vars.all()) {
            if (v.export()) continue;
            write("static ");
            context.types.write(v.type().must()).write(' ');
            varName(v).endStmt();
        }
        vars.bfs(this::write);
        newLine();
    }

    /**
     * 全局变量定义（运行时初始化值默认化，真实初始化走 Feng$globals_init）。
     */
    private ReleaserWriter write(GlobalVariable v) {
        if (v.export()) {
            if (context.header) write("extern ");
        } else {
            if (context.header) return this;
            write("static ");
        }
        var t = v.type().must();
        context.types.write(t).write(' ');
        varName(v);
        if (v.export() && context.header) return endStmt();
        write(" = ");
        // 运行时初始化（new/call/块表达式）不是合法 C 静态初始化器——默认值，
        // 真实初始化在 constructor（Feng$globals_init）
        if (v.value().has() && isRuntimeInit(v.value().must())) {
            if (t instanceof ArrayTypeDeclarer atd && atd.refer().has())
                write("{NULL, 0}");
            else if (t.maybeRefer().has()) write("NULL");
            else write("{}");
            return endStmt();
        }
        v.value().use(e -> context.exprs.writeValue(e, t), () -> {
            if (t.maybeRefer().has()) write("NULL");
            else write("{}");
        });
        return endStmt();
    }

    /**
     * 初始化器是否需要运行时求值（非 C 编译期常量）。
     */
    private boolean isRuntimeInit(Expression e) {
        if (e instanceof NewExpression || e instanceof CallExpression
                || e instanceof MethodExpression || e instanceof BlockExpression)
            return true;
        if (e instanceof VariableExpression)
            return true;
        if (e instanceof TupleExpression te)
            return te.elements().stream().anyMatch(this::isRuntimeInit);
        if (e instanceof ObjectExpression oe)
            return oe.entries().values().stream().anyMatch(this::isRuntimeInit);
        if (e instanceof ArrayExpression ae)
            return ae.elements().stream().anyMatch(this::isRuntimeInit);
        if (e instanceof BinaryExpression be)
            return isRuntimeInit(be.left()) || isRuntimeInit(be.right());
        if (e instanceof UnaryExpression ue)
            return isRuntimeInit(ue.operand());
        if (e instanceof ParenExpression pe)
            return isRuntimeInit(pe.child());
        return false;
    }

    // ===================================================================
    //  运行时全局初始化（constructor / destructor）
    // ===================================================================

    /**
     * 运行时全局初始化 constructor（强引用全局在 destructor 释放，先于泄漏检查）。
     */
    public void writeGlobalInits() {
        boolean anyInit = context.table.dagVars.all().stream()
                .anyMatch(v -> v.value().has() && isRuntimeInit(v.value().must()));
        boolean anyCleanup = context.table.dagVars.all().stream()
                .anyMatch(v -> {
                    var t = v.type().must();
                    return t.maybeRefer().match(r -> r.isKind(ReferKind.STRONG))
                            || context.stmts.needsDestroy(t);
                });
        if (!anyInit && !anyCleanup) return;
        if (anyInit) {
            write("__attribute__((constructor)) static void Feng$globals_init(void) {").indent();
            for (var v : context.table.dagVars.all()) {
                v.value().use(e -> {
                    if (isRuntimeInit(e)) {
                        context.exprs.varName(v).write(" = ");
                        context.exprs.writeValue(e, v.type().must());
                        endStmt();
                    }
                });
            }
            dedent().write('}').newLine();
        }
        if (anyCleanup) {
            write("__attribute__((destructor(102))) static void Feng$globals_cleanup(void) {").indent();
            for (var v : context.table.dagVars.all()) {
                var t = v.type().must();
                if (t.maybeRefer().match(r -> r.isKind(ReferKind.STRONG))
                        || context.stmts.needsDestroy(t)) {
                    // 释放全局强引用（SRef 数组走 cleanup_arr；final 类/接口/boxed 走槽位清理）
                    if (t instanceof ArrayTypeDeclarer atd) {
                        write("Feng$cleanup_arr_").write(Mangle.typeKey(atd.element()))
                                .write("(&").varName(v).write(')').endStmt();
                    } else if (t.maybeRefer().match(r -> r.isKind(ReferKind.STRONG))) {
                        // 按类型路由：final 类 → Feng$cleanup_<key>（静态 destroy）；
                        // 非 final/接口 → Feng$cleanup_sref/_ns（虚派发）；boxed →
                        // Feng$cleanup_free/_ns。不能无条件 Feng$release（虚派发
                        // Feng$vDestroy 对无 $meta 的 final/boxed 读野指针 → 段错误）。
                        write(context.stmts.strongRefCleanupFn(t))
                                .write("(&").varName(v).write(')').endStmt();
                    }
                }
            }
            dedent().write('}').newLine();
        }
    }

    // ===================================================================
    //  cleanup（ReleaserBuilder 已生成 AST，此处只 dump）
    // ===================================================================

    /**
     * cleanup 前置声明：{@code static inline void Feng$cleanup_<key>(<td>* p);}
     * （source 内 FENG$DEC 引用需先声明后定义）。
     * 按符号名去重：refer 变体（如 {@code [*?#]byte} 与 {@code [*#]byte}）cleanup 名
     * 相同（Mangle.cleanupName 只依赖元素类型），避免重复声明。
     */
    public void declareCleanups() {
        if (context.table.cleanups.isEmpty()) return;
        writeComment("cleanup declarations");
        var emitted = new java.util.HashSet<String>();
        for (var e : context.table.cleanups.entrySet()) {
            var sym = symbolName(e.getValue().symbol());
            if (!emitted.add(sym)) continue;
            write("static inline void ").write(sym);
            write('(').writeCleanupParam(e.getKey()).write(')').endStmt();
        }
        newLine();
    }

    /**
     * cleanup 函数体：{@code static inline void Feng$cleanup_<key>(<td>* p) { body }}。
     * body 是 ReleaserBuilder 生成的 AST（引用参数变量 {@code p_<id>}）。
     * 按符号名去重（与 declareCleanups 一致）。
     */
    public void writeCleanups() {
        if (context.table.cleanups.isEmpty()) return;
        writeComment("cleanup functions");
        // cleanup 参数 p 是 C 层槽位指针：成员访问用 ->（AST 类型是结构体值）
        context.exprs.cleanupSlotPtr = true;
        try {
            var emitted = new java.util.HashSet<String>();
            for (var e : context.table.cleanups.entrySet()) {
                var sym = symbolName(e.getValue().symbol());
                if (!emitted.add(sym)) continue;
                write("static inline void ").write(sym);
                write('(').writeCleanupParam(e.getKey()).write(')');
                write(' ');
                context.stmts.write(e.getValue().procedure().must().body());
            }
        } finally {
            context.exprs.cleanupSlotPtr = false;
        }
        newLine();
    }

    /**
     * cleanup 参数：{@code <td>* p}——final 类 td 输出 {@code X*} 再补 {@code *} 成
     * {@code X**}。参数名固定 {@code p}（ReleaserBuilder body 用 SymbolExpression
     * {@code "p"} 引用，与 CWriter.write(Symbol) 发射一致）。
     */
    private ReleaserWriter writeCleanupParam(TypeDeclarer td) {
        context.types.write(td).write('*').write(' ');
        return write("p");
    }

    // ===================================================================
    //  copy（值类型含强引用内容的深拷贝；CopyBuilder 已生成 AST，此处只 dump）
    // ===================================================================

    /**
     * copy 前置声明：{@code static inline T Feng$copy_<key>(T src);}——返回值 + 按值
     * 传入（见 docs/value-copy.md 4.3）。与 declareCleanups 一致按符号名去重。
     */
    public void declareCopies() {
        if (context.table.copies.isEmpty()) return;
        writeComment("copy declarations");
        var emitted = new java.util.HashSet<String>();
        for (var e : context.table.copies.entrySet()) {
            var fd = e.getValue().must();
            var sym = symbolName(fd.symbol());
            if (!emitted.add(sym)) continue;
            write("static inline ");
            context.types.write(e.getKey());
            write(' ').write(sym);
            write('(').writeCopyParam(e.getKey()).write(')').endStmt();
        }
        newLine();
    }

    /**
     * copy 函数体：{@code static inline T Feng$copy_<key>(T src) { body }}。
     * body 是 CopyBuilder 生成的 AST（参数 {@code src} 用 SymbolExpression 引用，
     * 结尾 {@code return src;}）。return 依赖 {@code context.enterProc} 取返回类型，
     * 须临时挂上 copy 的 Procedure（cleanup 无 return，故无需）。
     */
    public void writeCopies() {
        if (context.table.copies.isEmpty()) return;
        writeComment("copy functions");
        var emitted = new java.util.HashSet<String>();
        for (var e : context.table.copies.entrySet()) {
            var fd = e.getValue().must();
            var sym = symbolName(fd.symbol());
            if (!emitted.add(sym)) continue;
            write("static inline ");
            context.types.write(e.getKey());
            write(' ').write(sym);
            write('(').writeCopyParam(e.getKey()).write(')');
            write(' ');
            var prev = context.enterProc;
            context.enterProc = fd.procedure().must();
            try {
                context.stmts.write(fd.procedure().must().body());
            } finally {
                context.enterProc = prev;
            }
        }
        newLine();
    }

    /**
     * copy 参数：{@code <td> src}——按值传入（无指针），参数名固定 {@code src}
     * （CopyBuilder body 用 SymbolExpression {@code "src"} 引用）。
     */
    private ReleaserWriter writeCopyParam(TypeDeclarer td) {
        context.types.write(td).write(' ');
        return write("src");
    }

    // ===================================================================
    //  destroy（cleanup 引用；ReleaserBuilder 已生成 AST，此处只 dump）
    // ===================================================================

    /**
     * destroy 前置声明：{@code void Feng$destroy_X(<X>* $self);}——参数必须是具体类
     * 指针（body 内 {@code $self->$field} 访问需要类型；不用 {@code void*}，
     * 与 emitDestroys 定义保持一致）。签名与 FuncWriter 方法定义同一路径。
     */
    public void declareDestroys() {
        boolean any = false;
        for (var meta : context.table.classMetas.values()) {
            var mf = meta.destroy();
            if (mf == null) continue;
            if (!any) {
                writeComment("destroy declarations");
                any = true;
            }
            context.funcs.writeSig(mf).endStmt();
        }
        if (any) newLine();
    }

    /**
     * destroy 函数体：{@code void Feng$destroy_X(<X>* $self) { body }}。
     * body 是 ReleaserBuilder 生成的 AST（self 用 CurrentExpression 引用，
     * ExprWriter 直写 {@code $self}）。签名经 {@link FuncWriter#writeSig}
     * （SelfParameter → {@code <X>* $self}），与 FuncWriter 方法定义同一路径。
     */
    public void writeDestroys() {
        writeComment("destroy functions");
        for (var meta : context.table.classMetas.values()) {
            var mf = meta.destroy();
            if (mf == null || mf.body().none()) continue;
            context.funcs.writeSig(mf).write(" {").indent();
            context.stmts.write(mf.body().must().body());
            dedent().write('}').newLine();
        }
        newLine();
    }

    // ===================================================================
    //  main / test runner [S]
    // ===================================================================

    /**
     * 入口：{@code -T} 测试模式（有 testcases）→ test runner；否则 main。
     * 分支与旧 CGenerator 5219–5223 一致。
     */
    public void entry() {
        if (context.table.test && !context.table.testcases.isEmpty()) {
            testRunner();
        } else {
            context.table.main.use(this::writeMain);
        }
    }

    /**
     * main：implFunc + 有参数时 FENG_MAIN_HAS_ARGS + 拼接 Main.c 资源。
     */
    private void writeMain(FunctionDefinition main) {
        writeComment("entry function");
        context.funcs.implFunc(main);
        newLine();

        if (!main.prototype().parameterSet().isEmpty()) {
            write("#define FENG_MAIN_HAS_ARGS").newLine();
            newLine();
        }

        try (var is = getResource(CGenerator.mainFile);
             var ir = new InputStreamReader(Objects.requireNonNull(is));
             var r = new BufferedReader(ir)) {
            r.lines().forEach(line -> write(line).newLine());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- test runner（-T 模式，旧 CGenerator 5227–5288 迁移）----

    /**
     * test runner：{@code Feng$TestEntry} 注册表 + {@code int main(void)} 循环
     * （setjmp 帧逐个跑 testcase，{@code testFilter} 过滤）。仅 {@code -T} 模式且有
     * testcases 时由 {@link #entry()} 调用。
     */
    private void testRunner() {
        writeComment("auto-generated test runner");
        // 模块 .h 已由 includeHeaders 引入；printf 需要 stdio.h
        write("#include <stdio.h>").newLine();
        newLine();

        // leak checker 定义（Main.c 同款——test runner 自带 main，不拼接 Main.c，
        // 而 Header.h 在 FENG_DEBUG_MEMORY 下引用 Feng$debug_list / feng$debug）
        write("#ifdef FENG_DEBUG_MEMORY").newLine();
        write("Feng$Header* Feng$debug_list = NULL;").newLine();
        newLine();
        write("int feng$debug(bool all) {").indent().newLine();
        write("printf(\"==== memory stat ====\\n\");").newLine();
        write("int total = 0, leaked = 0;").newLine();
        write("for (Feng$Header* h = Feng$debug_list; h; h = h->next) {").indent().newLine();
        write("total++;").newLine();
        write("int c = atomic_load((atomic_int*)&h->refcnt);").newLine();
        write("if (all || c != 0) {").indent().newLine();
        write("printf(\"ref=%d site=%p size=%lld\\n\", c, h->site, (long long)h->size);").newLine();
        write("if (c != 0) leaked++;").newLine();
        dedent().write("}").newLine();
        dedent().write("}").newLine();
        write("printf(\"==== end memory stat (total=%d, leaked=%d) ====\\n\", total, leaked);").newLine();
        write("return leaked;").newLine();
        dedent().write("}").newLine();
        newLine();
        // 优先级 101：晚于 globals_cleanup(102)，此时全局强引用已释放；
        // 若有泄漏块，直接 _Exit(1) 让测试进程非零退出（_Exit 不重入清理）。
        write("__attribute__((destructor(101))) static void Feng$debug_fini(void) {").indent().newLine();
        write("if (feng$debug(false) > 0) _Exit(1);").newLine();
        dedent().write("}").newLine();
        write("#endif").newLine();
        newLine();

        // Test entry struct
        write("typedef struct {").indent().newLine();
        write("const char* name;").newLine();
        write("void (*func)(void);").newLine();
        dedent().write("} Feng$TestEntry;").newLine();
        newLine();

        // Test registry
        write("static Feng$TestEntry Feng$tests[] = {").indent().newLine();
        for (var ts : context.table.testcases) {
            if (!context.table.testFilter.isEmpty()
                    && !context.table.testFilter.contains(ts.name().toString())) {
                continue;
            }
            write("{\"").write(ts.name().toString())
                    .write("\", &").write(ts).write("},").newLine();
        }
        write("{NULL, NULL}").newLine();
        dedent().write("};").newLine();
        newLine();

        // main function
        write("int main(void) {").indent().newLine();
        write("int passed = 0;").newLine();
        write("int failed = 0;").newLine();
        newLine();
        write("for (int i = 0; Feng$tests[i].name != NULL; i++) {").indent().newLine();
        write("printf(\"  RUN  %s ... \", Feng$tests[i].name);").newLine();
        write("fflush(stdout);").newLine();
        newLine();
        write("volatile Feng$ExFrame _frame = {.prev = Feng$ex_top};").newLine();
        write("Feng$ex_top = (Feng$ExFrame*)&_frame;").newLine();
        newLine();
        write("if (setjmp(*(jmp_buf*)&_frame.buf) == 0) {").indent().newLine();
        write("Feng$tests[i].func();").newLine();
        write("Feng$ex_top = _frame.prev;").newLine();
        write("printf(\"PASS\\n\");").newLine();
        write("passed++;").newLine();
        dedent().write("} else {").newLine();
        indent().write("Feng$ex_top = _frame.prev;").newLine();
        write("void* _ex = _frame.exception;").newLine();
        write("Feng$release_ns(&_ex);").newLine();
        write("printf(\"FAIL\\n\");").newLine();
        write("failed++;").newLine();
        dedent().write("}").newLine();
        dedent().write("}").newLine();
        newLine();
        write("printf(\"\\nResults: %d passed, %d failed, %d total\\n\",").newLine();
        write("       passed, failed, passed + failed);").newLine();
        write("return failed > 0 ? 1 : 0;").newLine();
        dedent().write("}").newLine();
        newLine();
    }

    // ---- 辅助 ----

    /**
     * 变量 C 名：全局变量用符号，局部用 {@code name_id}。
     */
    private ReleaserWriter varName(Variable v) {
        if (v instanceof GlobalVariable gv) {
            write(gv.symbol());
        } else {
            write(v.name());
        }
        return write('_').write(v.id());
    }

    private static InputStream getResource(String res) {
        var cl = Thread.currentThread().getContextClassLoader();
        return new BufferedInputStream(Objects.requireNonNull(
                cl.getResourceAsStream(res)));
    }
}
