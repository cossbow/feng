package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.meta.ClassMeta;
import org.cossbow.feng.analysis.meta.MethodFunc;
import org.cossbow.feng.ast.GlobalVariable;
import org.cossbow.feng.ast.dcl.ReferKind;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.Statement;
import org.cossbow.feng.util.ErrorUtil;

import java.util.List;

/**
 * 函数/方法层 writer（阶段 5）：非泛型函数（{@code functionList}）、具体化泛型函数
 * （{@code monoFuncs}）与类方法（{@code classMetas.methods()} 的 {@link MethodFunc}）
 * 的原型声明与函数体发射。
 *
 * <p>纯发射器：{@code monoFuncs} / {@code classMetas.methods()} 已由前置 pass
 * （mono2 / ClassMetadata）算好并去重，无 lazy 注册、无去重 set。函数与方法是
 * **同一套发射**——只是方法在首参位置多一个 {@code void *self}，方法体内
 * {@code CurrentExpression}（self 关键字）由 ExprWriter 直写 {@code _self}，
 * 定义处必须注入 {@code <Class> *const _self = self;}（旧 implMethod 4789 行同款）。
 *
 * <p>方法符号唯一权威：{@link ClassMeta#methodSymbol}（已写入 {@link MethodFunc#symbol}，
 * 本层直接写字符串，不重新拼接）；泛型函数符号 = {@code Mangle.symbol}（monoFuncs 自带）。
 */
public class FuncWriter extends CWriter<FuncWriter> {

    public FuncWriter(WriterContext context) {
        super(context);
    }

    // ===================================================================
    //  函数原型 [H]
    // ===================================================================

    /**
     * [H] 函数原型 / extern 声明（functionList + monoFuncs；main 原型 context.header 内声明）。
     * module 模式下仅 context.header（source 通过 include 本模块 .h 可见）；单模块 source 补发。
     */
    public void declareFunction() {
        if (context.table.module.has() && !context.header) return;
        writeComment("function declaration");
        for (var fd : context.table.functionList) {
            if (fd.generic().isEmpty() && fd.procedure().has()) {
                writePrototype(fd);
            }
        }
        // monoFuncs：含仅有 prototype 的声明条目（extern 引用，如跨模块泛型实例）
        for (var fd : context.table.monoFuncs) {
            writePrototype(fd);
        }
        context.table.main.use(fd -> {
            // 测试模式：test runner 自带 int main(void)，context.header 不声明用户 main 原型
            if (context.header && !context.table.test) writePrototype(fd);
        });
        newLine();
    }

    /**
     * 函数原型：{@code <ret> <symbol>(<params>);}。
     */
    private void writePrototype(FunctionDefinition fd) {
        var pt = fd.prototype();
        pt.returnSet().use(t -> context.types.write(t), () -> write("void"));
        write(' ').write(fd.symbol());
        write('(').write(pt.parameterSet()).write(')');
        endStmt();
    }

    // ===================================================================
    //  方法原型 [H]
    // ===================================================================

    /**
     * [H] 方法原型：{@code <ret> <symbol>(void *self, <params>);}。
     * {@code MethodFunc.prototype()} 不含 self，首参固定补 {@code void *self}。
     * 归属规则与函数原型一致（module 模式仅 context.header；单模块 source 补发）。
     */
    public void declareMethods() {
        if (context.table.module.has() && !context.header) return;
        if (context.table.classMetas.isEmpty()) return;
        writeComment("method declaration");
        for (var meta : context.table.classMetas.values()) {
            for (var mf : meta.methods()) {
                writeMethodPrototype(mf);
            }
        }
        newLine();
    }

    FuncWriter writeSig(Prototype pt, Runnable namer) {
        pt.returnSet().use(t -> context.types.write(t), () -> write("void"));
        write(' ');
        namer.run();
        write('(');
        write(pt.parameterSet());
        write(')');
        return this;
    }

    FuncWriter writeSig(MethodFunc mf, String symbol) {
        var pt = mf.prototype();
        pt.returnSet().use(t -> context.types.write(t), () -> write("void"));
        write(' ').write(symbol).write('(');
        write(pt.parameterSet());
        write(')');
        return this;
    }

    FuncWriter writeSig(MethodFunc mf) {
        return writeSig(mf.prototype(), () -> write(mf.symbol()));
    }

    private void writeMethodPrototype(MethodFunc mf) {
        writeSig(mf).endStmt();
    }

    // ===================================================================
    //  函数定义 [S]
    // ===================================================================

    /**
     * [S] 函数定义（functionList + monoFuncs 函数体；source only）。
     */
    public void functionDefinition() {
        if (context.header) return;
        writeComment("function definition");
        for (var fd : context.table.functionList) {
            if (fd.generic().isEmpty() && fd.procedure().has()) {
                implFunc(fd);
            }
        }
        for (var fd : context.table.monoFuncs) {
            if (fd.procedure().has()) implFunc(fd);
        }
        newLine();
    }

    /**
     * 函数定义：{@code <ret> <symbol>(<params>) { body }}（source only）。
     */
    public void implFunc(FunctionDefinition fd) {
        if (!fd.generic().isEmpty()) return;
        if (fd.procedure().none()) return;
        var pt = fd.prototype();
        pt.returnSet().use(t -> context.types.write(t), () -> write("void"));
        write(' ').write(fd.symbol());
        write('(').write(pt.parameterSet()).write(')');
        write(' ').write(fd.procedure().must());
    }

    // ===================================================================
    //  方法定义 [S]
    // ===================================================================

    /**
     * [S] 类方法定义（classMetas.methods() 全量方法体；source only）。
     */
    public void methodDefinition() {
        if (context.header) return;
        writeComment("method definition");
        for (var meta : context.table.classMetas.values()) {
            for (var mf : meta.methods()) {
                implMethod(mf);
            }
        }
        newLine();
    }

    /**
     * 方法定义：{@code <ret> <symbol>(<X>* $self, <params>) { body }}。
     * self 首参已在 {@code MethodFunc.prototype()}（SelfParameter），经
     * {@link #write(ParameterSet)} 统一发射；方法体内 self 关键字是
     * {@code CurrentExpression}，ExprWriter 直写 {@code $self}。
     */
    private void implMethod(MethodFunc mf) {
        if (mf.body().none()) return;
        writeSig(mf).newLine();
        write(mf.body().must());
    }

    // ===================================================================
    //  共享函数体模板
    // ===================================================================

    /**
     * 函数体：{@code { _feng_fn_label:; 参数cleanup; body }}。
     */
    private FuncWriter write(Procedure proc) {
        context.enterProc = proc;
        write('{').indent();
        // 异常标签（checkIndex/throw 引用）
        write("_feng_fn_label:;").newLine();
        writeParamCleanupDecls(proc.prototype());
        context.stmts.write(proc.body());
        if (noTerminal(proc.body().list())) exitScope(proc);
        dedent().write('}').newLine();
        context.enterProc = null;
        return this;
    }

    /**
     * 参数列表：{@code <type> <varName>, ...}（有变量名则带名）。
     */
    private FuncWriter write(ParameterSet ps) {
        for (int i = 0; i < ps.size(); i++) {
            if (i > 0) write(", ");
            var p = ps.get(i);
            if (p instanceof FixedParameter fp) {
                var v = fp.var();
                if (v.none()) context.types.write(fp.type());
                else {
                    context.types.write(fp.type()).write(' ');
                    varName(v.get());
                }
            } else if (p instanceof SelfParameter sp) {
                context.types.write(sp.type());
                write(" $self");
            } else {
                ErrorUtil.unreachable();
            }
        }
        return this;
    }

    /**
     * 强引用参数：声明 {@code name_id_own} 副本挂 FENG$DEC，出作用域自动清理。
     */
    private void writeParamCleanupDecls(Prototype pt) {
        for (var p : pt.parameterSet()) {
            if (!(p instanceof FixedParameter fp)) continue;
            var vo = fp.var();
            if (vo.none()) continue;
            var v = vo.get();
            var t = v.type().must();
            var ref = t.maybeRefer();
            if (ref.none() || !ref.get().isKind(ReferKind.STRONG)) continue;
            context.types.write(t).write(' ');
            write('$').write(v.name().value()).write('_').write(v.id()).write("_own");
            write(" FENG$DEC(").write(context.stmts.strongRefCleanupFn(t)).write(')');
            write(" = ");
            context.exprs.varName(v);
            endStmt();
        }
    }

    // ---- 辅助 ----

    /**
     * 变量 C 名：全局变量用符号，局部用 {@code name_id}。
     */
    private FuncWriter varName(Variable v) {
        if (v instanceof GlobalVariable gv) {
            write(gv.symbol());
        } else {
            write(v.name());
        }
        return write('_').write(v.id());
    }

    private boolean noTerminal(List<Statement> list) {
        if (list.isEmpty()) return false;
        var last = list.getLast();
        return !(last instanceof org.cossbow.feng.ast.stmt.ReturnStatement
                || last instanceof org.cossbow.feng.ast.stmt.ThrowStatement);
    }

    private void exitScope(org.cossbow.feng.ast.Scope s) {
        // RAII 由 __attribute__((cleanup)) 自动处理
    }
}
