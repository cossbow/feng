package org.cossbow.feng.analysis.meta;

import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.analysis.hir.AddressOfException;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.var.Assignment;
import org.cossbow.feng.ast.var.FieldOperand;
import org.cossbow.feng.ast.var.VariableOperand;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.*;

/**
 * 释放分析 pass：为每个具体类生成 destroy（写入 {@link ClassMeta#destroy()}），
 * 为每个需要清理的强引用类型生成 cleanup（写入 {@link AnalyseSymbolTable#cleanups}）。
 * 两者都生成 AST，后端只 dump，不做释放逻辑。
 *
 * <p>字段释放全部内联展开（对齐旧 CGenerator.writeValueDestroy 的语义）：
 * <ul>
 *   <li>leaf（primitive / enum / func / struct 值）→ no-op</li>
 *   <li>SRef 数组 {@code [*]A} → {@code if (lv.$values && dec(lv.$values)) { 元素级联; free(lv.$values); }}</li>
 *   <li>定长数组 {@code [N]A} 值 → 内联 for 循环逐元素</li>
 *   <li>元组值 → 逐元素</li>
 *   <li>内嵌类值（无 refer 的类）→ 虚引用绑定 + {@code Feng$destroy_X}（不 dec/free）</li>
 *   <li>final 类强引用 → 内联 {@code if (lv && dec(lv)) { destroy_X(lv); free(lv); }}</li>
 *   <li>非 final / 接口强引用 → 内联 {@code if (lv && dec(lv)) { vDestroy(lv); free(lv); }}
 *       （等价 {@code Feng$release} 的 dec→虚派发→free，避免取地址）</li>
 *   <li>boxed（primitive / struct / enum 强引用）→ 内联 {@code if (lv && dec(lv)) { free(lv); }}
 *       （等价 {@code Feng$cleanup_free}，无 destroy）</li>
 *   <li>phantom → no-op（借用）</li>
 * </ul>
 * 内联化避免了 {@code &字段} 取地址：Fēng 类型系统没有「指向强引用字段」的类型，
 * 字段也不可能是虚引用（reference_zh.md「字段」节）。
 */
public final class ReleaserBuilder {

    private ReleaserBuilder() {
    }

    public static void build(AnalyseSymbolTable ast) {
        rewriteSyncStores(ast);
        for (var meta : ast.classMetas) {
            buildDestroy(meta);
        }
        buildCleanups(ast);
    }

    // ---- destroy ----

    private static void buildDestroy(ClassMeta meta) {
        var cd = meta.def();
        if (cd.builtin()) return;

        var symbol = Mangle.destroyName(cd.symbol());
        var pt = new Prototype(cd.pos(), new ParameterSet(cd.pos(),
                List.of(new SelfParameter(cd.pos(), cd))));
        var body = destroyBody(cd);
        var proc = new Procedure(cd.pos(), pt, body, Map.of());
        meta.destroy(new MethodFunc(new Identifier("destroy"), symbol,
                pt, Optional.of(proc), meta, false));
    }

    private static BlockStatement destroyBody(ClassDefinition cd) {
        var stmts = new ArrayList<Statement>();
        // 1. resource free 宏（析构顺序：先 free 后释放字段，reference_zh.md「资源类」）
        cd.resourceFree().use(rf -> stmts.add(callStmt(resourceSymbol(cd, rf),
                List.of(self(cd)))));
        // 2. release own fields（cd.fields()，非 allFields()；父类字段由父类 destroy 负责）
        for (var cf : cd.fields().values()) {
            releaseField(cf, self(cd), stmts, 0);
        }
        // 3. 父类 destroy（子先父后，跳过 Object/builtin）。
        //    用 cd.inherit()（mono2 已把泛型实参 mapIf 到 DerivedType）而非
        //    cd.parent() 模板类 + link()：对泛型继承（如 SealedBox`X`:BigBox`X`）
        //    link(模板) 空实参会 mismatch，且 destroy 符号须用具体化类
        //    Mangle.symbol(inherit)（BigBox_Int → Feng$destroy_BigBox_Int）。
        cd.inherit().use(idt -> {
            var pd = idt.def();
            if (pd.builtin()) return;

            var s = self(cd);
            s.expectType.set(new DerivedTypeDeclarer(idt.pos(), idt,
                    new Refer(idt.pos(), ReferKind.PHANTOM, true, false)));
            // 非泛型父类用原符号（Mangle.symbol 会给空泛型参数拼出 Base_ 尾缀）；
            // 泛型父类才用 mangle 符号（BigBox_Int → Feng$destroy_BigBox_Int）。
            var destroySym = idt.generic().isEmpty() ? idt.symbol() : Mangle.symbol(idt);
            stmts.add(callStmt(Mangle.destroySymbol(destroySym), List.of(s)));
        });
        return new BlockStatement(cd.pos(), stmts, false);
    }

    /**
     * resourceFree 宏方法符号：{@code [module$]Class$<macroId>}（ClassMeta.methodSymbol 公式）。
     */
    private static Symbol resourceSymbol(ClassDefinition cd, ClassMethod rf) {
        return new Symbol(cd.pos(), cd.symbol().module(),
                new Identifier(cd.symbol().name().value() + "$" + rf.name().value()));
    }

    /**
     * 释放一个类字段（字段名自带 {@code $} 前缀约定，后端 member 输出 {@code self->$name}）。
     */
    private static void releaseField(ClassField cf, PrimaryExpression self,
                                     List<Statement> out, int depth) {
        // sync var 字段（@Sync 且 var，非 const）：指针可能带自旋锁位 bit 0，
        // 释放必须走 Header.h 的 lock-slot 清理（cleanup_sfield / cleanup_sfield_final）。
        var syncVarField = cf.type().markSync() && !cf.immutable();
        releaseValue(cf.type(), member(self, cf.name()), out, depth, syncVarField);
    }

    /**
     * 统一值释放：按类型递归生成释放语句（对齐旧 writeValueDestroy，全部内联，不取地址）。
     * {@code syncVarField} 仅对强引用类字段有意义（见 {@link #releaseStrongClassRef}）。
     */
    private static void releaseValue(TypeDeclarer td, PrimaryExpression lv,
                                     List<Statement> out, int depth, boolean syncVarField) {
        var ref = td.maybeRefer();
        if (ref.match(r -> r.isKind(ReferKind.PHANTOM))) return; // 借用：no-op
        if (isLeafValue(td)) return; // 无强引用内容：no-op
        if (td instanceof ArrayTypeDeclarer atd) {
            if (ref.match(r -> r.isKind(ReferKind.STRONG))) {
                releaseSRefArray(atd, lv, out, depth);
            } else {
                releaseFixedArray(atd, lv, out, depth);
            }
            return;
        }
        if (td instanceof TupleTypeDeclarer ttd) {
            int i = 0;
            for (var et : ttd.elements()) {
                // unnamed：tuple 成员 C 字段名是 v<i>（无 $ 前缀），
                // write(Identifier) 对非 unnamed 标识符加 $ → .$v0 报错
                releaseValue(et, member(lv, new Identifier(Position.ZERO, "v" + i, true)),
                        out, depth, false);
                i++;
            }
            return;
        }
        if (td instanceof DerivedTypeDeclarer dtd) {
            var def = dtd.def();
            if (ref.none()) {
                // 内嵌类值：值类型字段可被虚引用（reference_zh.md），绑定后传给 destroy_X
                if (def instanceof ClassDefinition cd) {
                    releaseEmbeddedValue(dtd, lv, out, depth);
                }
                return;
            }
            if (ref.get().isKind(ReferKind.STRONG)) {
                if (def instanceof ClassDefinition cd) {
                    releaseStrongClassRef(dtd, lv, out, syncVarField);
                    return;
                }
                if (def instanceof InterfaceDefinition) {
                    releaseStrongClassRef(dtd, lv, out, false); // 接口 → vDestroy 虚派发
                    return;
                }
            }
            // boxed（struct / enum 强引用）：dec→free，无 destroy
            releaseBoxed(td, lv, out);
            return;
        }
        if (ref.match(r -> r.isKind(ReferKind.STRONG))) {
            // boxed（primitive 强引用）：dec→free，无 destroy
            releaseBoxed(td, lv, out);
        }
    }

    /**
     * SRef 数组 {@code [*]A}：{@code if (lv.$values && dec(lv.$values)) { 元素级联; free(lv.$values); }}。
     */
    private static void releaseSRefArray(ArrayTypeDeclarer atd, PrimaryExpression lv,
                                         List<Statement> out, int depth) {
        var values = member(lv, new Identifier("values"));
        var dec = call(decFn(atd), List.of(values));
        var cond = new BinaryExpression(atd.pos(), BinaryOperator.AND, values, dec);
        var yes = new ArrayList<Statement>();
        if (!isLeafValue(atd.element())) {
            var len = member(lv, new Identifier("length"));
            yes.add(elemLoop(atd.element(), values, len, depth + 1));
        }
        yes.add(callStmt(rt("free"), List.of(values)));
        out.add(new IfStatement(atd.pos(), Optional.empty(), cond,
                new BlockStatement(atd.pos(), yes), Optional.empty()));
    }

    /**
     * 定长数组值 {@code [N]A}：{@code for (i < N) 释放 lv.$values[i]}。
     */
    private static void releaseFixedArray(ArrayTypeDeclarer atd, PrimaryExpression lv,
                                          List<Statement> out, int depth) {
        var values = member(lv, new Identifier("values"));
        var len = atd.length().must();
        out.add(elemLoop(atd.element(), values, len, depth + 1));
    }

    /**
     * 逐元素释放循环：{@code for (Int64 i<depth> = 0; i<depth> < len; i<depth>++) { 释放 values[i<depth>] }}。
     */
    private static Statement elemLoop(TypeDeclarer elem, PrimaryExpression values,
                                      Expression len, int depth) {
        var pos = values.pos();
        var in = new Identifier("i" + depth);
        var it = Primitive.INT64.declarer(pos);
        var dv = new IntegerLiteral(pos, 0).expr();
        var iv = new Variable(pos, Modifier.empty(), Declare.CONST, in,
                Lazy.of(it), Lazy.of(dv));
        var ie = new VariableExpression(pos, iv, new Symbol(in));
        len.resultType.set(it);
        var cond = new BinaryExpression(pos, BinaryOperator.LT, ie, len);
        cond.resultType.set(Primitive.BOOL.declarer(pos));
        var ue = new BinaryExpression(pos, BinaryOperator.ADD, ie,
                new IntegerLiteral(pos, 1).expr());
        ue.resultType.set(it);
        var oi = new VariableOperand(pos, new Symbol(in));
        oi.variable().set(iv);
        oi.type.set(it);
        var ui = new Assignment(pos, oi, ue);
        var update = new AssignmentsStatement(pos, List.of(ui));
        var body = new ArrayList<Statement>();
        releaseValue(elem, new IndexOfExpression(pos, values, ie), body, depth, false);
        var init = new DeclarationStatement(pos, List.of(iv));
        return new ConditionalForStatement(pos, new BlockStatement(pos, body),
                Optional.of(init), cond, Optional.of(update));
    }

    /**
     * 强引用类/接口字段释放：
     * <ul>
     *   <li>sync var 字段（syncVarField，@Sync 且 var）→ Header.h 锁位清理：
     *       final 类调 {@code Feng$cleanup_sfield_final(&lv, destroy_X)}（无 $meta，静态 destroy）；
     *       非 final / 接口调 {@code Feng$cleanup_sfield(&lv)}（vDestroy 虚派发）。</li>
     *   <li>final 非 sync → 内联 {@code if (lv && dec(lv)) { destroy_X(lv); free(lv); }}</li>
     *   <li>非 final / 接口非 sync → 内联 {@code if (lv && dec(lv)) { vDestroy(lv); free(lv); }}
     *       （等价 {@code Feng$release((void**)&lv)}，但无需取地址）。</li>
     * </ul>
     */
    private static void releaseStrongClassRef(DerivedTypeDeclarer dtd, PrimaryExpression lv,
                                              List<Statement> out, boolean syncVarField) {
        // 具体化类的析构符号必须用 destroySymbolOf(dtd)（基于 dtd.derivedType() 的
        // mangle 符号，如 Node_Int_Void），而非 dtd.def().symbol()（模板符号 Node）——
        // mapIf 只改 DerivedType 的 generic，def 仍指向模板类，直接用会拼出
        // Feng$destroy_std$container$Node（缺 _Int_Void 后缀）。
        var cd = dtd.def() instanceof ClassDefinition c ? c : null;
        if (syncVarField) {
            var addr = new AddressOfException(lv);
            // @Sync var 字段：指针可能带锁位 bit 0，须经 Header.h 掩码后 dec/destroy/free
            if (cd != null && cd.isFinal()) {
                var args = new ArrayList<Expression>();
                args.add(addr);
                args.add(new SymbolExpression(lv.pos(),
                        Mangle.destroySymbol(destroySymbolOf(dtd)), TypeArguments.EMPTY));
                out.add(callStmt(rt("cleanup_sfield_final"), args));
            } else {
                out.add(callStmt(rt("cleanup_sfield"), List.of(addr)));
            }
            return;
        }
        var dec = call(decFn(lvType(lv)), List.of(lv));
        var cond = new BinaryExpression(lv.pos(), BinaryOperator.AND, lv, dec);
        var yes = new ArrayList<Statement>();
        yes.add(callStmt(cd != null && cd.isFinal()
                        ? Mangle.destroySymbol(destroySymbolOf(dtd)) : rt("vDestroy"),
                List.of(lv)));
        yes.add(callStmt(rt("free"), List.of(lv)));
        out.add(new IfStatement(lv.pos(), Optional.empty(), cond,
                new BlockStatement(lv.pos(), yes), Optional.empty()));
    }

    /**
     * boxed 强引用：{@code if (lv && dec(lv)) { free(lv); }}（等价 {@code Feng$cleanup_free}）。
     */
    private static void releaseBoxed(TypeDeclarer td, PrimaryExpression lv,
                                     List<Statement> out) {
        var dec = call(decFn(td), List.of(lv));
        var cond = new BinaryExpression(lv.pos(), BinaryOperator.AND, lv, dec);
        var yes = new ArrayList<Statement>();
        yes.add(callStmt(rt("free"), List.of(lv)));
        out.add(new IfStatement(lv.pos(), Optional.empty(), cond,
                new BlockStatement(lv.pos(), yes), Optional.empty()));
    }

    /**
     * 内嵌类值（值类型字段）：值类型字段可被虚引用（reference_zh.md「虚引用类型」），
     * 声明 {@code const r &X = lv;} 绑定地址，再调 {@code Feng$destroy_X(r)}（不 dec/free）。
     * <p>类型直接复用传入 {@code dtd.derivedType()}（含具体化实参）——不能对泛型类
     * 模板 {@code cd.link(pos)} 空实参重建（GenericMap mismatch）；destroy 符号同理：
     * 具体化类须用 {@code Mangle.symbol(dtd.derivedType())}（Hashing_Int_Int），
     * 而非模板类的 {@code cd.symbol()}（Hashing）。
     */
    private static void releaseEmbeddedValue(DerivedTypeDeclarer dtd, PrimaryExpression lv,
                                             List<Statement> out, int depth) {
        var cd = (ClassDefinition) dtd.def();
        var pos = lv.pos();
        var t = new DerivedTypeDeclarer(pos, dtd.derivedType(),
                new Refer(pos, ReferKind.PHANTOM, true, false));
        var name = new Identifier("r" + depth);
        var v = new Variable(pos, Modifier.empty(), Declare.CONST, name,
                Lazy.of(t), Lazy.of(lv));
        out.add(new DeclarationStatement(pos, List.of(v)));
        out.add(callStmt(Mangle.destroySymbol(destroySymbolOf(dtd)),
                List.of(new VariableExpression(pos, v, new Symbol(name)))));
    }

    /**
     * 具体化类型（DerivedTypeDeclarer）对应的类符号：泛型实例用 mangle 符号
     * （{@code Hashing_Int_Int}），非泛型用原符号——与 destroyBody 父类 destroy 一致。
     */
    private static Symbol destroySymbolOf(DerivedTypeDeclarer dtd) {
        var dt = dtd.derivedType();
        return dt.generic().isEmpty() ? dt.symbol() : Mangle.symbol(dt);
    }

    /**
     * 字段表达式的类型（dec 变体按字段类型 sync 标记选择）。
     */
    private static TypeDeclarer lvType(PrimaryExpression lv) {
        return lv.resultType.has() ? lv.resultType.must()
                : Primitive.INT.declarer(Position.ZERO);
    }

    /**
     * leaf 值：无需任何释放。
     */
    private static boolean isLeafValue(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) return ptd.refer().none();
        if (td instanceof EnumTypeDeclarer) return true;
        if (td instanceof FuncTypeDeclarer) return true;
        if (td instanceof GenericTypeDeclarer) return true;
        if (td instanceof DerivedTypeDeclarer dtd && dtd.refer().none()
                && dtd.def() instanceof StructureDefinition) return true;
        return false;
    }

    /**
     * dec 变体：{@code Feng$dec} 或 {@code Feng$dec_ns}（sync 标记）。
     */
    private static Symbol decFn(TypeDeclarer td) {
        return rt(td != null && td.markSync() ? "dec" : "dec_ns");
    }

    // ---- cleanup ----

    private static void buildCleanups(AnalyseSymbolTable ast) {
        var types = new LinkedHashSet<TypeDeclarer>();
        for (var meta : ast.classMetas.values()) {
            for (var cf : meta.def().allFields().values()) {
                collect(types, cf.type(), new LinkedHashSet<>());
            }
        }
        for (var gv : ast.constVars) gv.type().use(t -> collect(types, t, new LinkedHashSet<>()));
        for (var gv : ast.dagVars) gv.type().use(t -> collect(types, t, new LinkedHashSet<>()));
        for (var fd : ast.functionList) collectProto(types, fd.prototype());
        for (var fd : ast.monoFuncs) collectProto(types, fd.prototype());
        // main 原型参数（Main.c 拼接的 FENG_MAIN_HAS_ARGS 分支引用其 cleanup，
        // 如 Feng$cleanup_arr_ArraySRef_Byte 内部调用 Feng$cleanup_arr_Byte）
        ast.main.use(fd -> collectProto(types, fd.prototype()));
        // 方法原型参数/返回值：值类型含强引用内容的参数（如 take(b Box) 的 Box）
        // 也会生成 _own 影子变量并引用 cleanup 函数，须在此收集，否则函数体
        // 未以局部变量/字段形式使用该类型时 cleanup 未生成 → 后端链接失败。
        for (var meta : ast.classMetas) {
            for (var mf : meta.methods().values()) {
                collectProto(types, mf.prototype());
            }
        }
        // 函数/方法体局部变量与表达式：FENG$DEC 引用的强引用类型
        // （局部数组 `[*]T` 的 cleanup_arr、局部 final 类、值类型 cleanup_val）
        for (var fd : ast.functionList) {
            fd.procedure().use(proc -> preScanBody(types, proc.body()));
        }
        for (var fd : ast.monoFuncs) {
            fd.procedure().use(proc -> preScanBody(types, proc.body()));
        }
        ast.main.use(fd -> fd.procedure().use(proc -> preScanBody(types, proc.body())));
        for (var meta : ast.classMetas.values()) {
            for (var mf : meta.methods().values()) {
                mf.body().use(proc -> preScanBody(types, proc.body()));
            }
        }

        for (var td : types) {
            if (!ast.cleanups.containsKey(td)) {
                ast.cleanups.put(td, cleanupFunc(td));
            }
        }
    }

    /**
     * 预扫描函数/方法体：收集局部变量声明类型与其初始化表达式（pin 化
     * BlockExpression）里出现的类型——旧 CGenerator preScanCleanupStmts/
     * preScanCleanupExpr 的等价移植。
     */
    private static void preScanBody(Set<TypeDeclarer> out, Statement stmt) {
        if (stmt instanceof DeclarationStatement ds) {
            for (var v : ds.variables()) {
                collect(out, v.type().must(), new LinkedHashSet<>());
                v.value().use(e -> preScanExpr(out, e));
            }
        } else if (stmt instanceof BlockStatement bs) {
            // pin 化变量挂在 stack()（SemanticAnalyzer 2316 bs.stack(be.stack())），
            // 必须扫 stack 变量类型（如 `new([4]int)` 的 feng$pin_9 = [*]int）
            for (var v : bs.stack()) {
                if (v.type().has()) collect(out, v.type().must(), new LinkedHashSet<>());
            }
            for (var s : bs.list()) preScanBody(out, s);
        } else if (stmt instanceof IfStatement is) {
            is.init().use(s -> preScanBody(out, s));
            preScanBody(out, is.yes());
            is.not().use(s -> preScanBody(out, s));
        } else if (stmt instanceof ConditionalForStatement cfs) {
            cfs.initializer().use(s -> preScanBody(out, s));
            preScanBody(out, cfs.body());
        } else if (stmt instanceof SwitchStatement ss) {
            for (var br : ss.branches()) preScanBody(out, br);
        } else if (stmt instanceof TryStatement ts) {
            preScanBody(out, ts.body());
            for (var cc : ts.catchClauses()) preScanBody(out, cc.body());
            ts.finallyClause().use(s -> preScanBody(out, s));
        } else if (stmt instanceof ReturnStatement rs) {
            rs.result().use(e -> preScanExpr(out, e));
        } else if (stmt instanceof CallStatement cs) {
            preScanExpr(out, cs.call());
        } else if (stmt instanceof AssignmentsStatement as) {
            for (var a : as.list()) preScanExpr(out, a.value());
        }
    }

    private static void preScanExpr(Set<TypeDeclarer> out, Expression e) {
        if (e instanceof BlockExpression be) {
            for (var v : be.stack()) {
                if (v.type().has()) collect(out, v.type().must(), new LinkedHashSet<>());
            }
            for (var s : be.block()) preScanBody(out, s);
            preScanExpr(out, be.result());
        } else if (e instanceof CallExpression ce) {
            preScanExpr(out, ce.callee());
            for (var a : ce.arguments()) preScanExpr(out, a);
        } else if (e instanceof BinaryExpression be) {
            preScanExpr(out, be.left());
            preScanExpr(out, be.right());
        } else if (e instanceof TupleExpression te) {
            for (var el : te.elements()) preScanExpr(out, el);
        } else if (e instanceof ArrayExpression ae) {
            for (var el : ae.elements()) preScanExpr(out, el);
        } else if (e instanceof MemberOfExpression me) {
            preScanExpr(out, me.subject());
        } else if (e instanceof IndexOfExpression ie) {
            preScanExpr(out, ie.subject());
            preScanExpr(out, ie.index());
        } else if (e instanceof MethodExpression me) {
            preScanExpr(out, me.subject());
        }
    }

    private static void collectProto(Set<TypeDeclarer> out, Prototype pt) {
        pt.returnSet().use(t -> collect(out, t, new LinkedHashSet<>()));
        for (var p : pt.parameterSet()) {
            if (p instanceof FixedParameter fp) collect(out, fp.type(), new LinkedHashSet<>());
        }
    }

    private static void collect(Set<TypeDeclarer> out, TypeDeclarer td, Set<TypeDeclarer> seen) {
        if (td == null || !seen.add(td)) return;
        // 含类型变量的类型是 mono2 未具体化的残留（模板函数/类字段），
        // 不生成 per-type cleanup（后端没有对应 C 类型定义）。
        if (td.hasTypeVar()) return;
        var ref = td.maybeRefer();
        if (ref.has() && ref.get().isKind(ReferKind.STRONG)) {
            if (needsCleanup(td)) out.add(td);
            if (td instanceof ArrayTypeDeclarer atd) collect(out, atd.element(), seen);
            return;
        }
        // 值类型含强引用内容（定长数组/元组/内嵌类值）→ cleanup_val_<key>
        // （declareVar 的 valueCleanupFn 引用，旧 CGenerator registerValueCleanup 语义）
        if (ref.none() && !isLeafValue(td) && needsDestroy(td)) {
            out.add(td);
        }
        if (td instanceof ArrayTypeDeclarer atd) {
            collect(out, atd.element(), seen);
            return;
        }
        if (td instanceof TupleTypeDeclarer ttd) {
            for (var e : ttd.elements()) collect(out, e, seen);
            return;
        }
        if (td instanceof DerivedTypeDeclarer dtd && dtd.def() instanceof ClassDefinition cd) {
            for (var cf : cd.allFields().values()) collect(out, cf.type(), seen);
        }
    }

    /**
     * 值类型是否含需要释放的强引用内容（旧 CGenerator needsDestroy 733–746 语义，
     * 无 monoResolve——新后端类型已具体化）。
     */
    private static boolean needsDestroy(TypeDeclarer t) {
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

    /**
     * 需要 per-type cleanup 的类型：SRef 数组与 final 类强引用。
     * 非 final / 接口强引用回退运行时 {@code Feng$release/_ns}（虚派发），
     * boxed 回退 {@code Feng$cleanup_free/_ns}——都不生成 per-type 函数。
     */
    private static boolean needsCleanup(TypeDeclarer td) {
        if (td instanceof ArrayTypeDeclarer) return true;
        return td instanceof DerivedTypeDeclarer dtd
                && dtd.def() instanceof ClassDefinition cd && cd.isFinal();
    }

    private static FunctionDefinition cleanupFunc(TypeDeclarer td) {
        var symbol = Mangle.cleanupSymbol(td);
        var prototype = new Prototype(td.pos(), new ParameterSet(td.pos()));
        var p = pVar(td);
        var proc = new Procedure(td.pos(), prototype,
                new BlockStatement(td.pos(), cleanupBody(td, p)), Map.of());
        return new FunctionDefinition(td.pos(), Modifier.empty(), symbol,
                TypeParameters.empty(), proc);
    }

    /**
     * cleanup 函数体（p 是后端内部建模的 C 槽位指针：数组为 {@code Feng$ArraySRef_<ek>*}，
     * final 类为 {@code X**}，值类型为 {@code T*}）：
     * <ul>
     *   <li>SRef 数组：{@code if (p->$values && dec(p->$values)) { 元素级联; free(p->$values); }}</li>
     *   <li>final 类：{@code if (*p && dec(*p)) { destroy_X(*p); free(*p); }}</li>
     *   <li>值类型（定长数组/元组/内嵌类值）：对 {@code *p} 做统一值释放（releaseValue）</li>
     * </ul>
     */
    private static List<Statement> cleanupBody(TypeDeclarer td, PrimaryExpression p) {
        var stmts = new ArrayList<Statement>();
        if (td instanceof ArrayTypeDeclarer atd) {
            if (td.maybeRefer().match(r -> r.isKind(ReferKind.STRONG))) {
                releaseSRefArray(atd, p, stmts, 0);
            } else {
                // 值类型定长数组：释放 *p 的内容
                releaseValue(td, deref(p), stmts, 0, false);
            }
        } else if (td instanceof TupleTypeDeclarer ttd) {
            // 值类型元组：释放 *p 的内容
            releaseValue(td, deref(p), stmts, 0, false);
        } else if (td instanceof DerivedTypeDeclarer dtd
                && dtd.def() instanceof ClassDefinition cd) {
            if (dtd.refer().none()) {
                // 值类型内嵌类值：释放 *p 的内容（destroy_X(&lv)）
                releaseValue(td, deref(p), stmts, 0, false);
                return stmts;
            }
            var star = deref(p);
            var dec = call(decFn(td), List.of(star));
            var cond = new BinaryExpression(td.pos(), BinaryOperator.AND, star, dec);
            var yes = new ArrayList<Statement>();
            // destroy 符号用具体化类型的符号（dtd 可能携带泛型实参，cd.symbol() 是模板名）
            yes.add(callStmt(Mangle.destroySymbol(destroySymbolOf(dtd)), List.of(star)));
            yes.add(callStmt(rt("free"), List.of(star)));
            stmts.add(new IfStatement(td.pos(), Optional.empty(), cond,
                    new BlockStatement(td.pos(), yes), Optional.empty()));
        }
        return stmts;
    }

    // ---- 辅助构造 ----

    private static TypeDeclarer link(TypeDefinition def) {
        var dt = def.link();
        return new DerivedTypeDeclarer(def.pos(), dt,
                new Refer(def.pos(), ReferKind.PHANTOM, true, false));
    }

    private static PrimaryExpression self(ClassDefinition cd) {
        var e = new CurrentExpression(cd.pos(), cd.symbol(), true);
        // expectType / resultType 都要设置：expectType 供虚引用参数（&expr），
        // resultType 供 ofMember 判 ->（destroy 内 self->$field 是 X* 指针访问）
        var t = link(cd);
        e.expectType.set(t);
        e.resultType.set(t);
        return e;
    }

    private static PrimaryExpression pVar(TypeDeclarer td) {
        // unnamed 标识符：CWriter.write(Identifier) 对非 unnamed 标识符加 $ 前缀，
        // 参数名固定 p（ReleaserWriter.writeCleanupParam 同款）必须无 $。
        var e = new SymbolExpression(Position.ZERO,
                new Symbol(new Identifier(Position.ZERO, "p", true)),
                TypeArguments.EMPTY);
        e.expectType.set(td);
        e.resultType.set(td);
        return e;
    }

    private static Symbol rt(String name) {
        return new Symbol(Position.ZERO, Optional.of(Mangle.FENG), new Identifier(name));
    }

    private static MemberOfExpression member(PrimaryExpression subject, Identifier name) {
        return new MemberOfExpression(subject.pos(), subject, name, TypeArguments.EMPTY);
    }

    private static DereferExpression deref(PrimaryExpression e) {
        return new DereferExpression(e.pos(), e);
    }

    private static CallExpression call(Symbol sym, List<Expression> args) {
        return new CallExpression(sym.pos(),
                new SymbolExpression(sym.pos(), sym, TypeArguments.EMPTY), args, false);
    }

    private static Statement callStmt(Symbol sym, List<Expression> args) {
        return new CallStatement(sym.pos(), call(sym, args));
    }

    // ---- sync var 字段赋值改写（store_sl，写入前置）----

    /**
     * 遍历所有函数/方法体，把 sync var 字段赋值改写为 {@code Feng$store_sl((void**)&field, value)} 调用。
     * 非 {@code markSync} 的引用字段赋值不处理（走普通赋值路径）。
     * 改写通过 {@link Assignment#replacer()} 落地（后端按 replacer 发射，语义分析已用它展开复合赋值）。
     */
    private static void rewriteSyncStores(AnalyseSymbolTable ast) {
        for (var fd : ast.functionList) {
            fd.procedure().use(p -> scanStmts(p.body()));
        }
        for (var fd : ast.monoFuncs) {
            fd.procedure().use(p -> scanStmts(p.body()));
        }
        for (var meta : ast.classMetas) {
            for (var mf : meta.methods()) {
                mf.body().use(p -> scanStmts(p.body()));
            }
        }
        ast.main.use(fd -> fd.procedure().use(p -> scanStmts(p.body())));
    }

    private static void scanStmts(Statement s) {
        if (s instanceof AssignmentsStatement as) {
            for (var a : as.list()) rewriteAssign(a);
            return;
        }
        if (s instanceof BlockStatement bs) {
            for (var x : bs.list()) scanStmts(x);
            return;
        }
        if (s instanceof IfStatement is) {
            is.init().use(ReleaserBuilder::scanStmts);
            scanStmts(is.yes());
            is.not().use(ReleaserBuilder::scanStmts);
            return;
        }
        if (s instanceof ConditionalForStatement cfs) {
            cfs.initializer().use(ReleaserBuilder::scanStmts);
            scanStmts(cfs.body());
            cfs.updater().use(ReleaserBuilder::scanStmts);
            return;
        }
        if (s instanceof IterableForStatement ifs) {
            scanStmts(ifs.body());
            return;
        }
        if (s instanceof SwitchStatement ss) {
            ss.init().use(ReleaserBuilder::scanStmts);
            for (var br : ss.branches()) scanStmts(br);
            ss.defaultBranch().use(ReleaserBuilder::scanStmts);
            return;
        }
        if (s instanceof TryStatement ts) {
            scanStmts(ts.body());
            for (var cc : ts.catchClauses()) scanStmts(cc);
            ts.finallyClause().use(ReleaserBuilder::scanStmts);
            return;
        }
        if (s instanceof LabeledStatement ls) {
            scanStmts(ls.target());
            return;
        }
        if (s instanceof Branch br) {
            scanStmts(br.body());
            return;
        }
        if (s instanceof DeclarationStatement ds) {
            for (var v : ds.variables()) v.value().use(ReleaserBuilder::scanExpr);
            return;
        }
        if (s instanceof ReturnStatement rs) {
            rs.result().use(ReleaserBuilder::scanExpr);
            return;
        }
        if (s instanceof CallStatement cs) {
            scanExpr(cs.call());
        }
    }

    /**
     * 深入表达式中的块表达式（RelayLowering 的 pin 块可能含赋值）。
     */
    private static void scanExpr(Expression e) {
        if (e instanceof BlockExpression be) {
            for (var s : be.block()) scanStmts(s);
            scanExpr(be.result());
            return;
        }
        if (e instanceof CallExpression ce) {
            scanExpr(ce.callee());
            for (var a : ce.arguments()) scanExpr(a);
            return;
        }
        if (e instanceof BinaryExpression be) {
            scanExpr(be.left());
            scanExpr(be.right());
            return;
        }
        if (e instanceof TupleExpression te) {
            for (var el : te.elements()) scanExpr(el);
            return;
        }
        if (e instanceof ArrayExpression ae) {
            for (var el : ae.elements()) scanExpr(el);
            return;
        }
        if (e instanceof MemberOfExpression me) {
            scanExpr(me.subject());
            return;
        }
        if (e instanceof IndexOfExpression ie) {
            scanExpr(ie.subject());
            scanExpr(ie.index());
            return;
        }
        if (e instanceof MethodExpression me) {
            scanExpr(me.subject());
        }
    }

    /**
     * 改写单个赋值：operand 是 sync var 字段（强引用类字段且类型 {@code markSync}）时，
     * 生成 {@code Feng$store_sl(slotArg, value)} 并挂到 {@link Assignment#replacer()}。
     * 非 {@code markSync} 不处理（普通赋值路径）；非类字段（本地变量/数组元素/解引用）不处理。
     */
    private static void rewriteAssign(Assignment a) {
        if (!(a.operand() instanceof FieldOperand fo)) return;
        var ft = fo.type().must();
        if (!ft.markSync()) return;
        if (!(ft instanceof DerivedTypeDeclarer dtd)) return; // 类/接口强引用；数组 sync 槽位暂不走此路径
        // 参数1：字段表达式（MemberOfExpression），expectType 设为虚引用 → 后端统一输出 &field
        var slot = fo.rhs();
        slot.expectType.set(new DerivedTypeDeclarer(slot.pos(), dtd.derivedType(),
                new Refer(slot.pos(), ReferKind.PHANTOM, true, false)));
        var pos = a.pos();
        var call = new CallExpression(pos,
                new SymbolExpression(pos, rt("store_sl"), TypeArguments.EMPTY),
                List.of(slot, a.value()), false);
        a.replacer().set(new CallStatement(pos, call));
    }
}
