package org.cossbow.feng.analysis.meta;

import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassField;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.var.Assignment;
import org.cossbow.feng.ast.var.VariableOperand;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.*;

/**
 * 值类型（refer none）含强引用内容的 copy 函数生成（浅拷贝修复）。
 *
 * <p>语义对齐 docs/value-copy.md：值类型字段/元素若是强引用，C 结构体裸浅拷贝
 * 只复制指针不增引用计数。为每个 {@code needsCopy} 的值类型生成
 * {@code T Feng$copy_<typeKey>(T src)}——按值传入 src（C 已做结构体浅拷贝），
 * 逐子类型原地 inc 强引用、递归深拷贝嵌套值类型，最后 {@code return src}。
 *
 * <p>与 {@link ReleaserBuilder} 的关系：{@code needsCopy} 是 {@code needsDestroy}
 * 的子集（含强引用 → 含需释放内容），因此复用 {@code AnalyseSymbolTable.cleanups}
 * 已收集的类型键集过滤即可，无需重复扫描。copy 函数之间无相互引用（嵌套值类型
 * 原地递归展开），故 Lazy 仅作占位（与 copies 字段类型一致），无惰性求值需求。
 */
public final class CopyBuilder {

    private CopyBuilder() {
    }

    public static void build(AnalyseSymbolTable ast) {
        // ReleaserBuilder.buildCleanups 已收集所有「需释放」类型；needsCopy ⊆ needsDestroy，
        // 直接在其键集上过滤「值类型 + 含强引用内容」。
        for (var td : ast.cleanups.keySet()) {
            if (td.maybeRefer().none() && needsCopy(td)
                    && !ast.copies.containsKey(td)) {
                ast.copies.put(td, Lazy.of(copyFunc(td)));
            }
        }
    }

    /**
     * 值类型 t（refer none）是否含强引用内容（递归：类成员 / 数组·元组元素）。
     * 公开供 StmtWriter / ExprWriter 复用。
     */
    public static boolean needsCopy(TypeDeclarer td) {
        return containsStrongRef(td);
    }

    /** 任意类型 td 是否含强引用内容（递归：字段 / 元素）。 */
    private static boolean containsStrongRef(TypeDeclarer td) {
        var ref = td.maybeRefer();
        if (ref.match(r -> r.isKind(ReferKind.STRONG))) return true;    // 强引用内容 → 复制时需 inc
        if (ref.match(r -> r.isKind(ReferKind.PHANTOM))) return false;  // 借用 → 直拷
        if (isLeafValue(td)) return false;                              // primitive/enum/func/struct → 直拷
        if (td instanceof ArrayTypeDeclarer atd) return containsStrongRef(atd.element());  // 元素
        if (td instanceof TupleTypeDeclarer ttd) {                                        // 元素
            for (var e : ttd.elements()) if (containsStrongRef(e)) return true;
            return false;
        }
        if (td instanceof DerivedTypeDeclarer dtd && dtd.def() instanceof ClassDefinition cd) {
            for (var cf : cd.allFields().values())                            // 成员（含继承字段）
                if (containsStrongRef(cf.type())) return true;
            return false;
        }
        return false;
    }

    private static boolean isLeafValue(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) return ptd.refer().none();
        if (td instanceof EnumTypeDeclarer) return true;
        if (td instanceof FuncTypeDeclarer) return true;
        if (td instanceof GenericTypeDeclarer) return true;
        if (td instanceof DerivedTypeDeclarer dtd && dtd.refer().none()
                && dtd.def() instanceof StructureDefinition) return true;
        return false;
    }

    // ---- copy 函数体 ----

    private static FunctionDefinition copyFunc(TypeDeclarer td) {
        var symbol = Mangle.copySymbol(td);
        // 签名：T Feng$copy_<key>(T src)——参数 src 由 ReleaserWriter 手工发射
        // （同 cleanup 的 p 参数），原型仅带返回值类型供 return 语句使用。
        var prototype = new Prototype(td.pos(), new ParameterSet(td.pos()), td);
        var src = srcVar(td);
        var body = new ArrayList<Statement>();
        copyValue(td, src, body, 0);
        body.add(new ReturnStatement(td.pos(), Optional.of(src)));
        var proc = new Procedure(td.pos(), prototype,
                new BlockStatement(td.pos(), body), Map.of());
        return new FunctionDefinition(td.pos(), Modifier.empty(), symbol,
                TypeParameters.empty(), proc);
    }

    /**
     * 递归生成「原地深拷贝」语句：lv 已是 C 结构体浅拷贝后的值，逐子类型补齐引用计数。
     */
    private static void copyValue(TypeDeclarer td, PrimaryExpression lv,
                                  List<Statement> out, int depth) {
        var ref = td.maybeRefer();
        if (ref.match(r -> r.isKind(ReferKind.STRONG))) {
            incStrongRef(td, lv, out);
            return;
        }
        if (ref.match(r -> r.isKind(ReferKind.PHANTOM))) return; // 借用：no-op
        if (isLeafValue(td)) return;                             // leaf：no-op
        if (td instanceof ArrayTypeDeclarer atd) {
            // 定长数组值：逐元素
            var values = member(lv, new Identifier("values"));
            var len = atd.length().must();
            out.add(copyElemLoop(atd.element(), values, len, depth + 1));
            return;
        }
        if (td instanceof TupleTypeDeclarer ttd) {
            int i = 0;
            for (var et : ttd.elements()) {
                // unnamed：tuple 成员 C 字段名是 v<i>（无 $ 前缀）
                copyValue(et, member(lv, new Identifier(Position.ZERO, "v" + i, true)),
                        out, depth);
                i++;
            }
            return;
        }
        if (td instanceof DerivedTypeDeclarer dtd
                && dtd.def() instanceof ClassDefinition cd) {
            // 内嵌类值：递归字段
            for (var cf : cd.allFields().values()) {
                copyValue(cf.type(), member(lv, cf.name()), out, depth);
            }
        }
    }

    /**
     * 强引用：inc 指针（class/interface/boxed 直接 inc lv；SRef 数组 inc lv.$values）。
     */
    private static void incStrongRef(TypeDeclarer td, PrimaryExpression lv,
                                     List<Statement> out) {
        if (td instanceof ArrayTypeDeclarer) {
            var values = member(lv, new Identifier("values"));
            out.add(incIf(values, td));
            return;
        }
        out.add(incIf(lv, td));
    }

    /** {@code if (lv) Feng$inc[_ns](lv);} */
    private static Statement incIf(PrimaryExpression lv, TypeDeclarer td) {
        var incSym = incFn(td);
        var yes = new ArrayList<Statement>();
        yes.add(callStmt(incSym, List.of(lv)));
        return new IfStatement(lv.pos(), Optional.empty(), lv,
                new BlockStatement(lv.pos(), yes), Optional.empty());
    }

    /**
     * 逐元素拷贝循环：{@code for (Int64 i<depth> = 0; i<depth> < len; i<depth>++) { copyValue } }
     * 与 ReleaserBuilder.elemLoop 同构（仅 body 换成 copyValue）。
     */
    private static Statement copyElemLoop(TypeDeclarer elem, PrimaryExpression values,
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
        copyValue(elem, new IndexOfExpression(pos, values, ie), body, depth);
        var init = new DeclarationStatement(pos, List.of(iv));
        return new ConditionalForStatement(pos, new BlockStatement(pos, body),
                Optional.of(init), cond, Optional.of(update));
    }

    // ---- 辅助构造 ----

    /** copy 函数参数 src（unnamed，C 名不带 $），类型 td（值类型）。 */
    private static PrimaryExpression srcVar(TypeDeclarer td) {
        var e = new SymbolExpression(Position.ZERO,
                new Symbol(new Identifier(Position.ZERO, "src", true)),
                TypeArguments.EMPTY);
        e.expectType.set(td);
        e.resultType.set(td);
        return e;
    }

    /** inc 变体：{@code Feng$inc} 或 {@code Feng$inc_ns}（sync 标记，与 decFn 对称）。 */
    private static Symbol incFn(TypeDeclarer td) {
        return rt(td != null && td.markSync() ? "inc" : "inc_ns");
    }

    private static Symbol rt(String name) {
        return new Symbol(Position.ZERO, Optional.of(Mangle.FENG), new Identifier(name));
    }

    private static MemberOfExpression member(PrimaryExpression subject, Identifier name) {
        return new MemberOfExpression(subject.pos(), subject, name, TypeArguments.EMPTY);
    }

    private static CallExpression call(Symbol sym, List<Expression> args) {
        return new CallExpression(sym.pos(),
                new SymbolExpression(sym.pos(), sym, TypeArguments.EMPTY), args, false);
    }

    private static Statement callStmt(Symbol sym, List<Expression> args) {
        return new CallStatement(sym.pos(), call(sym, args));
    }
}
