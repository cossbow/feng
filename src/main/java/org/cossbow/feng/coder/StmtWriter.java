package org.cossbow.feng.coder;

import org.cossbow.feng.ast.GlobalVariable;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Mangle;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.var.*;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Groups;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 语句发射器（阶段 4b）。
 *
 * <p>纯发射器：按 AST 逐节点单遍写 C 文本。**零 register-then-emit 可变状态**——
 * {@code loopLabels} / {@code tryFinallyDepth} 是立即使用的调用栈跟踪
 * （进入循环注册、退出即用），不收集、不延迟发射。
 *
 * <p>关键规则（`docs/expr-stmt-writer.md` §4）：
 * <ul>
 *   <li>**{@code Assignment.replacer()} 必须处理**：sync var 字段写入已由
 *       ReleaserBuilder 前置为 {@code Feng$store_sl} 调用挂在其上，本类零 sync 判断；</li>
 *   <li>强引用赋值先清后赋（数组用 {@code cleanup_arr_<ek>} + 类型化临时变量）；</li>
 *   <li>强引用局部变量声明挂 {@code FENG$DEC}（cleanups 查表，查不到回退
 *       {@code Feng$release/_ns} / {@code Feng$cleanup_free/_ns}）；</li>
 *   <li>try/catch/finally：{@code volatile Feng$ExFrame} + setjmp 模式 + finally 返回跟踪。</li>
 * </ul>
 */
public class StmtWriter extends CWriter<StmtWriter> {

    public StmtWriter(WriterContext context) {
        super(context);
    }

    // ---- try-finally 返回跟踪 / 循环标签（调用栈跟踪，非延迟发射） ----

    private boolean insideTryFinally = false;
    private int tryFinallyDepth = 0;
    private final Map<ForStatement, Groups.G2<Label, Label>> loopLabels = new HashMap<>();

    // ===================================================================
    //  语句大分发
    // ===================================================================

    public StmtWriter write(Statement e) {
        switch (e) {
            case DeclarationStatement ee -> write(ee);
            case AssignmentsStatement ee -> write(ee);
            case BlockStatement ee -> write(ee);
            case BreakStatement ee -> write(ee);
            case CallStatement ee -> write(ee);
            case ContinueStatement ee -> write(ee);
            case ForStatement ee -> write(ee);
            case IfStatement ee -> write(ee);
            case LabeledStatement ee -> write(ee);
            case ReturnStatement ee -> write(ee);
            case SwitchStatement ee -> write(ee);
            case ThrowStatement ee -> write(ee);
            case TryStatement ee -> write(ee);
            case AssertStatement ee -> write(ee);
            case Branch ee -> write(ee);
            default -> ErrorUtil.unreachable();
        }
        return this;
    }

    public StmtWriter write(List<Statement> list) {
        for (var s : list) write(s);
        return this;
    }

    // ===================================================================
    //  声明 / 赋值
    // ===================================================================

    private StmtWriter write(DeclarationStatement ds) {
        ds.variables().forEach(this::declareVar);
        return this;
    }

    StmtWriter declareVar(TypeDeclarer t,
                          Runnable namer,
                          Runnable valuer) {
        context.exprs.writeType(t).write(' ');
        namer.run();
        // 强引用 → FENG$DEC(cleanupFn)：cleanups 查表（SRef 数组 / final 类），
        // 查不到（非 final/接口/boxed）回退运行时函数。
        var ref = t.maybeRefer();
        if (ref.has() && ref.get().isKind(ReferKind.STRONG)) {
            write(" FENG$DEC(").write(strongRefCleanupFn(t)).write(')');
        } else if (ref.none() && needsDestroy(t)) {
            write(" FENG$DEC(").write(valueCleanupFn(t)).write(')');
        }
        write(" = ");
        valuer.run();
        return endStmt();
    }

    /**
     * 强引用局部变量声明：{@code <T> <name> FENG$DEC(<cleanupFn>) = <init>;}
     * （旧 CGenerator 1663–1688；cleanups 已由 ReleaserBuilder 前置，本类只查表）。
     */
    StmtWriter declareVar(Variable v) {
        var t = v.type().must();
        return declareVar(t, () -> {
            context.exprs.varName(v);
        }, () -> {
            v.value().use(e -> context.exprs.writeValue(e, t), () -> {
                if (t instanceof ArrayTypeDeclarer) write("{}");
                else if (t.maybeRefer().has()) write("NULL");
                else write("{}");
            });
        });
    }

    private StmtWriter write(AssignmentsStatement as) {
        for (var a : as.list()) {
            // sync 字段写入已前置为 store_sl 调用（ReleaserBuilder），必须走 replacer
            if (a.replacer().has()) {
                write(a.replacer().must());
                continue;
            }
            writeAssign(a.operand(), a.value()).endStmt();
        }
        return this;
    }

    /**
     * 普通赋值（旧 3133–3170）：PHANTOM → castRef 直写；强引用 → 先清后赋；
     * 值类型 → 直写。sync 字段写入不走此路径（replacer 已处理）。
     */
    private StmtWriter writeAssign(Operand o, Expression v) {
        var t = o.type.must();
        var r = t.maybeRefer();
        if (r.has()) {
            if (r.get().isKind(ReferKind.PHANTOM)) {
                return write(o).write(" = ").castRef(v, t);
            }
            // 强引用：先求值新值（castRef），cleanup 旧槽位，再写入
            if (t instanceof ArrayTypeDeclarer atd) {
                // 数组 SRef：类型化临时变量 + 数组 cleanup
                var ek = Mangle.typeKey(atd.element());
                write(o).write(" = ({ Feng$ArraySRef_").write(ek).write(" _t = ");
                castRef(v, t);
                write("; Feng$cleanup_arr_").write(ek).write("(&");
                write(o);
                write("); _t; })");
            } else {
                // 简单指针：void* 临时变量 + 槽位 cleanup
                write(o).write(" = ({ void* _t = (void*)(");
                castRef(v, t);
                write("); ").write(strongRefCleanupFn(t)).write("(&");
                write(o);
                write("); _t; })");
            }
            return this;
        }
        // 值类型含强引用 → copy-then-swap（docs/value-copy.md 4.5.2，自赋值安全）。
        // unbound 源（临时/new/字面量）所有权已转移，_t 裸写即可，不套 copy。
        if (context.table.copies.containsKey(t)) {
            write(o).write(" = ({ ");
            context.types.write(t).write(" _t = ");
            context.exprs.writeValue(v, t);
            // 非 final 类值类型：$meta 是运行时类型标识，赋值不得覆盖。切片赋值
            // *r = a1（r 指向派生类 B）时 _t.$meta 是 A_meta，整体赋值会覆盖 B 的
            // $meta，破坏 vDestroy 虚派发 → 漏析构 B.j。保留目标原 $meta。
            if (isNonFinalValue(t)) {
                write("; _t.$meta = ");
                write(o).write(".$meta");
            }
            write("; ").write(valueCleanupFn(t)).write("(&");
            write(o);
            write("); _t; })");
            return this;
        }
        write(o).write(" = ");
        context.exprs.write(v);
        return this;
    }

    private StmtWriter castRef(Expression v, TypeDeclarer t) {
        context.exprs.castRef(v, t);
        return this;
    }

    /**
     * 非 final 类值类型（有 $meta 运行时类型标识，析构须虚派发）。final 类无 $meta，
     * 走静态 destroy，不参与切片赋值 $meta 保留。
     */
    private boolean isNonFinalValue(TypeDeclarer t) {
        return t instanceof DerivedTypeDeclarer dtd
                && dtd.def() instanceof ClassDefinition cd && !cd.isFinal();
    }

    // ===================================================================
    //  Operand 发射
    // ===================================================================

    private StmtWriter write(Operand e) {
        switch (e) {
            case IndexOperand ee -> write(ee);
            case TupleOperand ee -> write(ee);
            case FieldOperand ee -> write(ee);
            case VariableOperand ee -> write(ee);
            case DereferOperand ee -> write(ee);
            default -> ErrorUtil.unreachable();
        }
        return this;
    }

    private StmtWriter write(VariableOperand e) {
        context.exprs.varName(e.variable().must());
        return this;
    }

    private StmtWriter write(IndexOperand e) {
        var st = e.subject().resultType.must();
        context.exprs.write(e.subject());
        write(".$values[Feng$checkIndex(");
        context.exprs.write(e.index());
        write(',');
        if (st instanceof ArrayTypeDeclarer atd && atd.refer().none()) {
            write(atd.len());
        } else {
            context.exprs.write(e.subject());
            write(".$length");
        }
        write(", (Uint64)(uintptr_t)&&_feng_fn_label, ");
        write(e.pos().start() != null ? e.pos().start().getLine() : 0);
        return write(")]");
    }

    private StmtWriter write(TupleOperand e) {
        write('(');
        context.exprs.write(e.subject());
        return write(").v").write(e.index());
    }

    private StmtWriter write(FieldOperand e) {
        ofMember(e.subject());
        return write(e.field());
    }

    private StmtWriter write(DereferOperand e) {
        write("(*");
        context.exprs.write(e.subject());
        return write(')');
    }

    /**
     * subject 访问：数组/值类型用 {@code .}，引用用 {@code ->}。
     */
    private StmtWriter ofMember(Expression subject) {
        context.exprs.write(subject);
        var td = subject.resultType.must();
        if (td instanceof ArrayTypeDeclarer || td.maybeRefer().none())
            return write('.');
        return write("->");
    }

    // ===================================================================
    //  控制流
    // ===================================================================

    private StmtWriter write(BlockStatement bs) {
        if (bs.newScope()) write('{').indent();
        write(bs.list());
        if (bs.newScope()) {
            if (noTerminal(bs.list())) exitScope(bs);
            dedent().write('}').newLine();
        }
        return this;
    }

    private StmtWriter write(BreakStatement s) {
        var g = loopLabels.get(s.target.must());
        return write("goto ").write(g.b()).endStmt();
    }

    private StmtWriter write(ContinueStatement s) {
        var g = loopLabels.get(s.target.must());
        return write("goto ").write(g.a()).endStmt();
    }

    private StmtWriter write(Label label) {
        return write(label.name()).write('_').write(label.id());
    }

    private StmtWriter write(CallStatement e) {
        context.exprs.write(e.call()).endStmt();
        return this;
    }

    private StmtWriter write(ForStatement e) {
        return switch (e) {
            case ConditionalForStatement ee -> write(ee);
            case IterableForStatement ee -> write(ee.replace.must());
            case null, default -> ErrorUtil.unreachable();
        };
    }

    private StmtWriter write(ConditionalForStatement fs) {
        var lg = Groups.g2(
                new Label(new Identifier("loopNext")),
                new Label(new Identifier("loopExit")));
        loopLabels.put(fs, lg);
        write('{').indent();
        fs.initializer().use(this::write);
        write("for(;;) {").indent();
        write("if(");
        context.exprs.write(fs.condition());
        write("){").indent();
        write(fs.body());
        dedent().write("}else{").indent();
        write("break").endStmt();
        dedent().write('}').newLine();
        write(lg.a()).write(":").endStmt();
        fs.updater().use(this::write);
        dedent().write('}').newLine();
        write(lg.b()).write(":").endStmt();
        dedent().write('}').newLine();
        return this;
    }

    private StmtWriter write(IfStatement is) {
        is.init().use(s -> {
            write('{').indent();
            write(s);
        });
        write("if(");
        context.exprs.write(is.condition());
        write(')');
        write(is.yes());
        is.not().use(s -> write(" else ").write(s));
        if (is.init().has()) {
            dedent().write('}').newLine();
        }
        return this;
    }

    private StmtWriter write(LabeledStatement s) {
        return write(s.label()).write(':').write(s.target());
    }

    private StmtWriter write(ReturnStatement rs) {
        // try-finally 内：推迟返回，先执行 finally
        if (insideTryFinally) {
            int depth = tryFinallyDepth - 1;
            if (rs.result().none()) {
                write("_feng_returned").write(depth).write(" = true; ");
                write("goto _feng_finally_").write(depth).endStmt();
            } else {
                write("_feng_retval").write(depth).write(" = ");
                context.exprs.write(rs.result().get());
                endStmt();
                write("_feng_returned").write(depth).write(" = true; ");
                write("goto _feng_finally_").write(depth).endStmt();
            }
            return this;
        }
        if (rs.result().none()) return write("return").endStmt();
        var re = rs.result().get();
        var rt = context.enterProc.prototype().returnSet().must();
        // 强引用返回：返回值 inc，参数 cleanup 平衡
        write("return ");
        context.exprs.writeValue(re, rt, false);
        return endStmt();
    }

    private StmtWriter write(SwitchStatement ss) {
        if (ss.init().has()) {
            write('{');
            write(ss.init().get());
        }
        write("switch(");
        context.exprs.write(ss.value());
        write("){");
        for (var br : ss.branches()) {
            for (var cs : br.constants()) {
                write("case ");
                context.exprs.write(cs);
                write(':');
            }
            write(br);
            write("break;").newLine();
        }
        ss.defaultBranch().use(br -> {
            write("default: ");
            write(br);
        });
        write('}').newLine();
        if (ss.init().has()) write('}').newLine();
        return this;
    }

    private StmtWriter write(Branch e) {
        write(e.body());
        return this;
    }

    private StmtWriter write(ThrowStatement ts) {
        var exExpr = ts.exception();
        var td = (DerivedTypeDeclarer) exExpr.resultType.must();
        var cd = (ClassDefinition) td.def();

        // 找 trace 方法（Exception 或其子类定义）
        var traceMethod = findTraceMethod(cd);

        write("({ void* _ex = (void*)(");
        context.exprs.write(exExpr);
        write("); ");
        if (traceMethod != null) {
            var owner = traceMethod.master() != null
                    ? (ClassDefinition) traceMethod.master() : cd;
            write(owner.symbol());
            write("$trace(_ex, ");
        } else {
            write("Feng$errorSetTrace(_ex, ");
        }
        write("(Uint64)(uintptr_t)&&_feng_fn_label, ");
        write(ts.pos().start() != null ? ts.pos().start().getLine() : 0);
        write("); ");
        write("Feng$throw(_ex); __builtin_unreachable(); })");
        return endStmt();
    }

    private StmtWriter write(AssertStatement as) {
        if (!context.debug) return this;  // 非 context.debug 模式 no-op

        write("if (!(");
        context.exprs.write(as.condition());
        write(")) { ");
        write("({ $AssertException* _ex = Feng$alloc(sizeof($AssertException)); ");
        write("_ex->$meta = &Feng$meta_$AssertException; ");
        write("$Exception$trace(_ex, ");
        write("(Uint64)(uintptr_t)&&_feng_fn_label, ");
        write(as.pos().start() != null ? as.pos().start().getLine() : 0);
        write("); ");
        write("Feng$throw(_ex); __builtin_unreachable(); })");
        endStmt();
        write(" }");
        return this;
    }

    // ===================================================================
    //  try / catch / finally
    // ===================================================================

    private StmtWriter write(TryStatement ts) {
        boolean hasFinally = ts.finallyClause().has();
        boolean hasCatches = !ts.catchClauses().isEmpty();
        int depth = tryFinallyDepth;

        write('{').indent();

        // 返回跟踪：仅 finally 存在时需要（return 推迟到 finally 之后）
        if (hasFinally) {
            var proc = procOf(ts.body());
            if (proc != null && proc.returnSet().has()) {
                write("volatile ");
                context.exprs.writeType(proc.returnSet().must());
                write(" _feng_retval").write(depth).write("; ");
            }
            write("volatile bool _feng_returned").write(depth).write(" = false; ").newLine();
        }

        // 异常帧（volatile：值须在 longjmp 后存活，C11）
        write("volatile Feng$ExFrame _frame").write(depth);
        write(" = {.prev = Feng$ex_top}; ");
        write("Feng$ex_top = (Feng$ExFrame*)&_frame").write(depth).endStmt();

        write("if (setjmp(*(jmp_buf*)&_frame").write(depth).write(".buf) == 0) {").indent();

        // === TRY body ===
        if (hasFinally) {
            insideTryFinally = true;
            tryFinallyDepth++;
        }
        write(ts.body());
        if (hasFinally) {
            tryFinallyDepth--;
            if (tryFinallyDepth == 0) insideTryFinally = false;
        }
        dedent();

        if (hasCatches) {
            write("} else {").indent();
            write("void* _ex = _frame").write(depth).write(".exception;").newLine();

            boolean first = true;
            for (var cc : ts.catchClauses()) {
                if (first) {
                    write("if (");
                } else {
                    write(" else if (");
                }
                first = false;

                boolean firstType = true;
                for (var catchType : cc.typeSet()) {
                    if (!firstType) write(" || ");
                    firstType = false;
                    if (catchType instanceof DerivedTypeDeclarer dtd
                            && dtd.def() instanceof ClassDefinition ccd) {
                        // 类 → is_kind（支持父类匹配）
                        write("Feng$is_kind(Feng$objMeta(_ex), (const Feng$Meta*)&Feng$meta_");
                        write(ccd.symbol());
                        write(")");
                    } else if (catchType instanceof DerivedTypeDeclarer dtd
                            && dtd.def() instanceof InterfaceDefinition ifd) {
                        // 接口 → iface_vtable（支持接口匹配）
                        write("Feng$iface_vtable(Feng$objMeta(_ex), (const Feng$Meta*)&Feng$meta_");
                        write(ifd.symbol());
                        write(") != NULL");
                    } else {
                        ErrorUtil.unreachable();
                    }
                }
                write(") {").indent();

                // 声明 catch 变量：单类型用类型化指针
                var arg = cc.argument();
                if (cc.typeSet().size() == 1) {
                    var ctd = (DerivedTypeDeclarer) cc.typeSet().get(0);
                    var def = ctd.def();
                    if (def instanceof InterfaceDefinition) {
                        write("void* ").varName(arg).write(" = _ex;").newLine();
                    } else {
                        var ccd = (ClassDefinition) def;
                        write(ccd.symbol()).write("* ").varName(arg)
                                .write(" = (").write(ccd.symbol()).write("*)_ex;").newLine();
                    }
                } else {
                    write("void* ").varName(arg).write(" = _ex;").newLine();
                }

                write(cc.body());
                write("Feng$dec(_ex); ");  // 释放帧持有引用
                write("_frame").write(depth).write(".state = 1; /* caught */").newLine();
                dedent().write('}');
            }
            write(" { /* fallthrough */ }").newLine(); // 全部 catch 未匹配
            dedent();
        }
        write('}').newLine();

        write("Feng$ex_top = _frame").write(depth).write(".prev;").newLine();

        // === FINALLY ===
        if (hasFinally) {
            write("_feng_finally_").write(depth).write(':').newLine();
            write(ts.finallyClause().must());
            write("if (_feng_returned").write(depth).write(") return _feng_retval")
                    .write(depth).endStmt();
        }

        // 未处理异常重抛
        if (hasCatches) {
            write("if (_frame").write(depth)
                    .write(".state != 1 && _frame").write(depth)
                    .write(".exception) { Feng$throw(_frame").write(depth)
                    .write(".exception); }").newLine();
        } else if (hasFinally) {
            write("if (_frame").write(depth)
                    .write(".exception) { Feng$throw(_frame").write(depth)
                    .write(".exception); }").newLine();
        }

        dedent().write('}').newLine();
        return this;
    }

    // ===================================================================
    //  辅助
    // ===================================================================

    private boolean noTerminal(List<Statement> list) {
        if (list.isEmpty()) return false;
        var last = list.getLast();
        return !(last instanceof ReturnStatement
                || last instanceof ThrowStatement);
    }

    private void exitScope(org.cossbow.feng.ast.Scope s) {
        // RAII 由 __attribute__((cleanup)) 自动处理
    }

    /**
     * 从语句树中找 ReturnStatement 提取外层 Procedure 的原型（try-finally 返回类型）。
     */
    private Prototype procOf(Statement s) {
        if (s instanceof ReturnStatement rs && rs.procedure().has()) {
            return rs.procedure().must().prototype();
        }
        if (s instanceof BlockStatement bs) {
            for (var st : bs.list()) {
                var p = procOf(st);
                if (p != null) return p;
            }
        }
        if (s instanceof IfStatement is) {
            var p = procOf(is.yes());
            if (p != null) return p;
            if (is.not().has()) {
                p = procOf(is.not().get());
                if (p != null) return p;
            }
        }
        if (s instanceof ForStatement fs) {
            if (fs instanceof ConditionalForStatement cfs) {
                return procOf(cfs.body());
            }
        }
        if (s instanceof SwitchStatement ss) {
            for (var br : ss.branches()) {
                var p = procOf(br.body());
                if (p != null) return p;
            }
        }
        if (s instanceof TryStatement ts) {
            var p = procOf(ts.body());
            if (p != null) return p;
        }
        return null;
    }

    private ClassMethod findTraceMethod(ClassDefinition cd) {
        var traceId = new Identifier("trace");
        var m = cd.methods().tryGet(traceId);
        if (m.has()) return (ClassMethod) m.get();
        for (var p = cd.parent(); p.has(); p = p.get().get().parent()) {
            m = p.get().get().methods().tryGet(traceId);
            if (m.has()) return (ClassMethod) m.get();
        }
        return null;
    }

    // ---- 清理函数选择 ----

    /**
     * 强引用槽位（变量/参数/赋值目标）的 cleanup 函数：
     * SRef 数组 / final 类 → cleanups 查表（ReleaserBuilder 已生成）；
     * 非 final 类 / 接口 → Feng$cleanup_sref/_ns（void* adapter，FENG$DEC 兼容
     * 任意指针槽位；不能用 Feng$release——其 void** 参数与 T** 槽位不兼容）；
     * boxed → Feng$cleanup_free/_ns。
     * 包可见（NCGenerator 参数 cleanup 复用）。
     */
    String strongRefCleanupFn(TypeDeclarer t) {
        if (t instanceof ArrayTypeDeclarer atd) {
            return "Feng$cleanup_arr_" + Mangle.typeKey(atd.element());
        }
        var cleanup = context.table.cleanups.get(t);
        if (cleanup != null) {
            return Mangle.cleanupName(t);
        }
        if (isClassLikeStrongRef(t)) {
            return t.markSync() ? "Feng$cleanup_sref" : "Feng$cleanup_sref_ns";
        }
        return t.markSync() ? "Feng$cleanup_free" : "Feng$cleanup_free_ns";
    }

    /**
     * 值类型（定长数组/元组/内嵌类值）含强引用内容时的 cleanup 函数：
     * 与 ReleaserBuilder 生成的符号一致（{@code Mangle.cleanupName} 即
     * {@code cleanup_<typeKey>[_ns]} / {@code cleanup_arr_<ek>}），
     * 不再使用旧的 {@code cleanup_val_<key>} 命名。
     */
    private String valueCleanupFn(TypeDeclarer t) {
        return Mangle.cleanupName(t);
    }

    /**
     * 强引用目标是类/接口（有 $meta → 虚派发安全）。
     */
    private boolean isClassLikeStrongRef(TypeDeclarer t) {
        return t instanceof DerivedTypeDeclarer dtd
                && dtd.refer().match(r -> r.isKind(ReferKind.STRONG))
                && (dtd.def() instanceof ClassDefinition || dtd.def() instanceof InterfaceDefinition);
    }

    /**
     * 值类型是否含需要释放的强引用内容。包可见（NCGenerator 全局变量判断复用）。
     */
    boolean needsDestroy(TypeDeclarer t) {
        var ref = t.maybeRefer();
        if (ref.match(r -> r.isKind(ReferKind.STRONG))) return true;
        if (ref.match(r -> r.isKind(ReferKind.PHANTOM))) return false;  // borrow, no-op
        if (isLeafValue(t)) return false;
        if (t instanceof ArrayTypeDeclarer atd) return needsDestroy(atd.element());
        if (t instanceof TupleTypeDeclarer ttd) {
            for (var et : ttd.elements()) if (needsDestroy(et)) return true;
            return false;
        }
        return t instanceof DerivedTypeDeclarer dtd
                && dtd.def() instanceof ClassDefinition;
    }

    private boolean isLeafValue(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) return ptd.refer().none();
        if (td instanceof EnumTypeDeclarer) return true;
        if (td instanceof FuncTypeDeclarer) return true;
        if (td instanceof GenericTypeDeclarer) return true;
        if (td instanceof DerivedTypeDeclarer dtd && dtd.refer().none()
                && dtd.def() instanceof StructureDefinition) return true;
        return false;
    }

    /**
     * 变量 C 名：全局变量用符号，局部用 {@code name_id}。
     */
    private StmtWriter varName(Variable v) {
        if (v instanceof GlobalVariable gv) {
            write(gv.symbol());
        } else {
            write(v.name());
        }
        return write('_').write(v.id());
    }
}
