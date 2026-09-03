package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.hir.AddressOfException;
import org.cossbow.feng.analysis.meta.ClassMeta;
import org.cossbow.feng.analysis.meta.VTable;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.GenericType;
import org.cossbow.feng.ast.gen.PrimitiveType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.lit.Literal;
import org.cossbow.feng.ast.lit.NilLiteral;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.util.RepeatList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 表达式发射器（阶段 4a）。
 *
 * <p>纯发射器：按 AST 逐节点单遍写 C 文本，零 register-then-emit 状态。
 * 类型名发射统一走 {@link TypeWriter#write(TypeDeclarer)}（refer 分支
 * {@code T*} / {@code T**}），本类不手写 {@code *} 拼接。
 *
 * <p>关键规则（`docs/expr-stmt-writer.md` §3）：
 * <ul>
 *   <li>{@code castRef} 全套转换（同类型强引用复制 inc / 数组 reinterpret /
 *       类 upcast / 类→接口）；</li>
 *   <li>sync 字段读取：{@code isSyncVarField} 命中 → {@code Feng$load_sl}
 *       （返回值已 +1，调用点跳过 inc）；</li>
 *   <li>方法调用符号：优先查 {@code ClassMeta.methods()} 预解析符号
 *       （{@link #methodSymbolOf}），虚派发经扁平 vtable 槽直达
 *       （{@link #vtableOf} / {@link #isDynamicSlot}）。</li>
 * </ul>
 */
public class ExprWriter extends CWriter<ExprWriter> {

    private static final String COMMA = ", ";

    public ExprWriter(WriterContext context) {
        super(context);
    }

    /**
     * cleanup 函数体上下文标志：ReleaserBuilder 生成 cleanup 时参数 {@code p} 的
     * AST 类型是结构体值（{@code .} 访问），但 C 层签名是槽位指针
     * （{@code Feng$ArraySRef_<ek>*} / {@code X**}），成员访问须用 {@code ->}。
     * NCGenerator dump cleanup body 前置 true。
     */
    public boolean cleanupSlotPtr;

    // ===================================================================
    //  类型名发射（统一走 TypeWriter）
    // ===================================================================

    /**
     * 类型名（refer 分支）。返回 {@code this} 便于链式。包可见（StmtWriter 复用）。
     */
    ExprWriter writeType(TypeDeclarer td) {
        context.types.write(td);
        return this;
    }

    /**
     * 符号名或 mangle 名（泛型具体化）。
     */
    private ExprWriter writeType(DerivedType dt) {
        if (dt.def() instanceof EnumDefinition) {
            context.types.write(Primitive.INT);
            return this;
        }
        context.types.write(dt);
        return this;
    }

    /**
     * 基础类型符号名（不含引用指针）：primitive → C 类型名；derived → 类/结构体名。
     * 用于数组/标量 reinterpret cast 的 {@code (T*)} 目标。
     */
    private ExprWriter baseTypeSymbol(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) return write(ptd.primitive());
        if (td instanceof DerivedTypeDeclarer dtd) return writeType(dtd.derivedType());
        return ErrorUtil.unreachable();
    }

    // ===================================================================
    //  值转换入口 writeValue / writeArg / writeValues
    // ===================================================================

    public ExprWriter writeValue(Expression v, TypeDeclarer t) {
        if (context.table.copies.containsKey(t) && !v.unbound()) {
            return write(Mangle.copyName(t)).write('(')
                    .writeValue(v, t, false).write(')');
        }
        return writeValue(v, t, false);
    }

    /**
     * 把值转换为目标类型，处理引用包装与 cast。
     * {@code noRefInc}：调用点已知引用所有权转移（如 load_sl 已 +1）时跳过 inc。
     */
    public ExprWriter writeValue(Expression v, TypeDeclarer t, boolean noRefInc) {
        if (v instanceof LiteralExpression le) return writeLiteral(le, t);
        var r = t.maybeRefer();
        if (r.none()) return write(v);
        if (r.get().isKind(ReferKind.PHANTOM)) return referPhantom(v, t);
        return castRef(v, t, noRefInc);
    }

    /**
     * 参数列表发射（目标类型来自 prototype 参数集）。
     */
    public ExprWriter writeValues(List<Expression> values, List<TypeDeclarer> dstTypes) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) write(COMMA);
            writeValue(values.get(i), dstTypes.get(i));
        }
        return this;
    }

    // ===================================================================
    //  castRef —— 值 → 目标类型（引用包装与 cast）
    // ===================================================================

    /**
     * 包可见（StmtWriter 赋值路径复用）。
     */
    ExprWriter castRef(Expression v, TypeDeclarer t) {
        return castRef(v, t, false);
    }

    /**
     * 全套转换（旧 CGenerator 1730–1856 迁移，去掉 monoResolve——类型已具体化）。
     */
    private ExprWriter castRef(Expression v, TypeDeclarer t, boolean noRefInc) {
        var rt = v.resultType.must();
        if (t.baseTypeSame(rt)) {
            // SRef 数组 → PRef 数组：包装 .$values & .$length 字段
            if (t instanceof ArrayTypeDeclarer tat && rt instanceof ArrayTypeDeclarer rat
                    && tat.refer().has() && rat.refer().has()
                    && tat.refer().get().isKind(ReferKind.PHANTOM)
                    && !rat.refer().get().isKind(ReferKind.PHANTOM)) {
                return write('(').writeType(t).write("){").write(v)
                        .write(".$values, ").write(v).write(".$length}");
            }
            // 同类型强引用复制：Feng$inc[_ns]，除非源是 unbound（临时/new 已转移
            // 所有权）、IsExpression、load_sl 结果（已 +1）或调用点显式跳过。
            var needInc = t.maybeRefer().match(r -> r.isKind(ReferKind.STRONG))
                    && !v.unbound()
                    && !(v instanceof IsExpression)
                    && !noRefInc
                    && !(v instanceof MemberOfExpression moe && isSyncVarField(moe));
            // SRef 数组结构体：inc $values 指针，复制 {$values, $length}
            if (needInc && t instanceof ArrayTypeDeclarer tat) {
                var ek = Mangle.typeKey(tat.element());
                return write("(Feng$ArraySRef_").write(ek).write("){(")
                        .writeType(tat.element()).write(" *)").write(incFn(t)).write("((")
                        .write(v).write(").$values), (").write(v).write(").$length}");
            }
            if (needInc) write(incFn(t)).write('(');
            write(v);
            if (needInc) write(')');
            return this;
        }
        // 非 final 类 upcast：B* → A*（B extends A，扁平布局，简单指针 cast）
        if (rt instanceof DerivedTypeDeclarer rdt && t instanceof DerivedTypeDeclarer tdt
                && rdt.def() instanceof ClassDefinition rcd && !rcd.isFinal()
                && tdt.def() instanceof ClassDefinition tcd && !tcd.isFinal()
                && isSubclass(rcd, tcd)) {
            var needInc = t.maybeRefer().match(r -> r.isKind(ReferKind.STRONG))
                    && !v.unbound() && !(v instanceof IsExpression)
                    && !noRefInc;
            if (needInc) write(incFn(t)).write('(');
            write("((");
            writeType(tdt.derivedType());
            write(" *)(");
            write(v);
            write("))");
            if (needInc) write(')');
            return this;
        }
        // 类 → 接口：*!Class → *!Iface（接口引用是 void*）
        if (rt instanceof DerivedTypeDeclarer rdt && t instanceof DerivedTypeDeclarer tdt
                && rdt.def() instanceof ClassDefinition rcd
                && tdt.def() instanceof InterfaceDefinition tid
                && allIfaces(rcd).contains(tid)) {
            var needInc = t.maybeRefer().match(r -> r.isKind(ReferKind.STRONG))
                    && !v.unbound() && !(v instanceof IsExpression)
                    && !noRefInc;
            if (needInc) write(incFn(t)).write('(');
            write("(void*)(");
            write(v);
            write(")");
            if (needInc) write(')');
            return this;
        }
        if (t instanceof ArrayTypeDeclarer at) {
            if (rt.isNil()) {
                // nil → 数组引用结构体：空 {NULL, 0} 字面量
                return write('(').writeType(t).write("){NULL, 0}");
            }
            // 把源 reinterpret 成元素数组：{(E*)<data>, <byteSize>/sizeof(E)}
            var needInc = at.refer().match(r -> r.isKind(ReferKind.STRONG))
                    && !v.unbound() && !(v instanceof IsExpression)
                    && !noRefInc;
            write('(').writeType(t).write("){(").writeType(at.element()).write(" *)");
            if (needInc) write(incFn(t)).write('(');
            else write("(void*)");
            if (rt instanceof ArrayTypeDeclarer) {
                write('(').write(v).write(").$values");
            } else if (rt.maybeRefer().has()) {
                write('(').write(v).write(')');
            } else {
                write("&(").write(v).write(')');
            }
            if (needInc) write(')');
            write(", ");
            if (rt instanceof ArrayTypeDeclarer art) {
                write("(sizeof(").writeType(art.element()).write(')');
                if (art.refer().none()) write("*").write(art.len());
                else write("*(").write(v).write(").$length");
                write(')');
            } else {
                write("sizeof(").baseTypeSymbol(rt).write(')');
            }
            write("/sizeof(").writeType(at.element()).write(')');
            return write('}');
        }
        if (rt instanceof ArrayTypeDeclarer) {
            // 数组 → 单引用：(U*)data
            var needInc = t.maybeRefer().match(r -> r.isKind(ReferKind.STRONG))
                    && !v.unbound() && !(v instanceof IsExpression)
                    && !noRefInc;
            write("((").baseTypeSymbol(t).write(" *)");
            if (needInc) write(incFn(t)).write('(');
            else write("(void*)");
            write('(').write(v).write(").$values");
            if (needInc) write(')');
            return write(')');
        }
        // unrelated data-type reinterpret：(T*)(void*)ptr（primitive/struct）
        if (!rt.isNil() && t.maybeRefer().has() && isDataType(t)) {
            var needInc = t.maybeRefer().match(r -> r.isKind(ReferKind.STRONG))
                    && !v.unbound() && !(v instanceof IsExpression)
                    && !noRefInc;
            write("((").baseTypeSymbol(t).write(" *)");
            if (needInc) write(incFn(t)).write('(');
            else write("(void*)");
            if (rt.maybeRefer().none()) write("&(").write(v).write(')');
            else write('(').write(v).write(')');
            if (needInc) write(')');
            return write(')');
        }
        return write(v);
    }

    /**
     * primitive 或 plain struct —— 可安全做指针 reinterpret 的目标。
     */
    private boolean isDataType(TypeDeclarer t) {
        return t instanceof PrimitiveTypeDeclarer
                || (t instanceof DerivedTypeDeclarer dtd
                && dtd.def() instanceof StructureDefinition);
    }

    // ===================================================================
    //  sync 字段读取（backend-abi.md §4.4）
    // ===================================================================

    /**
     * sync var 字段判别：{@code markSync()} 且字段非 immutable
     * （读取 → load_sl、写入 → store_sl（ReleaserBuilder 已前置）、释放 → cleanup_sfield）。
     */
    private boolean isSyncVarField(MemberOfExpression e) {
        // ReleaserBuilder 生成的无类型访问：非 sync 字段，返回 false
        if (e.resultType.get().none()) return false;
        if (!e.resultType.must().markSync()) return false;
        var st = e.subject().resultType.get();
        if (st.none()) return false;
        if (!(st.get() instanceof DerivedTypeDeclarer dtd)
                || !(dtd.def() instanceof ClassDefinition cd)) return false;
        var cf = cd.allFields().tryGet(e.member());
        return cf.has() && !cf.get().immutable();
    }

    // ===================================================================
    //  sync-aware inc/dec 路由
    // ===================================================================

    /**
     * "Feng$inc" 或 "Feng$inc_ns"（按类型 sync 标记）。
     */
    String incFn(TypeDeclarer t) {
        return t.markSync() ? "Feng$inc" : "Feng$inc_ns";
    }

    // ===================================================================
    //  referPhantom —— 虚引用参数 &expr 统一规则（backend-abi.md §7）
    // ===================================================================

    /**
     * 虚引用（PHANTOM）目标转换：{@code &expr} 或数组 PRef 包装
     * （旧 CGenerator 1867–1924 迁移）。
     *
     * <p>中端把取地址意图建模为参数表达式 {@code expectType = 虚引用}；
     * 本方法按该虚引用类型 {@code t} 输出取地址形式：
     * 值类型目标 → {@code &v} / 物化复合字面量；引用目标 → castRef 传递；
     * 数组 → {@code Feng$ArrayPRef_<ek>} 包装。
     */
    private ExprWriter referPhantom(Expression v, TypeDeclarer t) {
        var vtOpt = v.resultType.get();
        if (vtOpt.none()) {
            // ReleaserBuilder 生成的无类型表达式（值类型字段/元素，如
            // releaseEmbeddedValue 的 r<N> 声明）：视为值类型目标取地址
            var base = t.derefer().getOrElse(t);
            if (v.unbound()) return write('(').writeType(base).write("[1]){").write(v).write('}');
            write('&');
            return write(v);
        }
        var vt = vtOpt.get();
        if (vt.maybeRefer().has()) {
            return castRef(v, t);
        }
        if (t.baseTypeSame(vt)) {
            if (!(vt instanceof ArrayTypeDeclarer avt)) {
                if (vt.maybeRefer().none()) {
                    if (v.unbound()) {
                        // rvalue → 块内复合字面量数组：lvalue 生命周期覆盖包围块，衰减为指针
                        return write('(').writeType(vt).write("[1]){")
                                .write(v).write('}');
                    }
                    write('&');
                }
                return write(v);
            }
            // 定长数组 → 按元素类型包装成 per-type Feng$ArrayPRef 结构体
            var elemType = avt.element();
            return write("(Feng$ArrayPRef_").write(Mangle.typeKey(elemType))
                    .write("){(void*)").write(v).write(".$values, ")
                    .write(avt.len()).write('}');
        }
        // 值类型子类 → 基类虚引用（如 B → &A）
        if (t instanceof DerivedTypeDeclarer tdt && tdt.refer().match(r -> r.isKind(ReferKind.PHANTOM))
                && tdt.def() instanceof ClassDefinition tcd && !tcd.isFinal()
                && vt instanceof DerivedTypeDeclarer vdt
                && vdt.def() instanceof ClassDefinition vcd && !vcd.isFinal()
                && isSubclass(vcd, tcd)) {
            write("((").writeType(tdt.derivedType()).write(" *)");
            // bound：直接取地址（扁平布局保证基类字段是前缀，切片赋值作用于原值，计数平衡）
            if (v.unbound()) write('(').writeType(vt).write("[1]){").write(v).write('}');
            else write('&').write(v);
            return write(')');
        }
        // 类值 → 接口引用：传对象地址（参数是 void*）
        if (t instanceof DerivedTypeDeclarer tdt
                && tdt.def() instanceof InterfaceDefinition) {
            if (v.unbound()) return write('(').writeType(vt).write("[1]){").write(v).write('}');
            write('&');
            return write(v);
        }
        if (t instanceof ArrayTypeDeclarer at && at.refer().has()
                && at.refer().get().isKind(ReferKind.PHANTOM)
                && vt instanceof ArrayTypeDeclarer avt
                && at.element().baseTypeSame(avt.element())) {
            // 同元素数组 → phantom 数组：PRef 包装
            write("(Feng$ArrayPRef_").write(Mangle.typeKey(at.element())).write("){");
            if (avt.refer().none())
                write("(void*)").write(v).write(".$values, ").write(avt.len());
            else
                write(v).write(".$values, ").write(v).write(".$length");
            return write('}');
        }
        // 其余（unrelated data types、标量↔数组 reinterpret）共享 castRef 逻辑
        return castRef(v, t);
    }

    // ===================================================================
    //  字面量
    // ===================================================================

    /**
     * 字面量 → 目标类型（旧 1926–1945）。
     */
    private ExprWriter writeLiteral(LiteralExpression v, TypeDeclarer t) {
        if (v.literal() instanceof NilLiteral) return castRef(v, t);
        if (v.literal() instanceof StringLiteral sl) {
            var r = t.maybeRefer();
            if (r.none()) return writeData(sl, t);
            write('(').write("Feng$").write(Mangle.typeKey(t)).write("){");
            if (r.get().isKind(ReferKind.PHANTOM)) {
                write("(void*)");
            } else {
                write(incFn(t)).write('(');
            }
            literalString(sl).write(".array.$values");
            if (r.get().isKind(ReferKind.PHANTOM))
                write(", ").write(sl.length());
            else
                write("), ").write(sl.length());
            return write('}');
        }
        return write(v);
    }

    /**
     * 字符串字面量 → 字节数组数据（值类型目标）。
     */
    private ExprWriter writeData(StringLiteral e, TypeDeclarer t) {
        write('(').writeType(t).write("){");
        for (byte b : e.value()) write(b).write(',');
        return write('}');
    }

    /**
     * 字符串池常量名：{@code Feng$constString_<id>}。
     */
    private ExprWriter literalString(StringLiteral sl) {
        return write("Feng$constString_").write(sl.id());
    }

    private ExprWriter writeLit(Literal lit, Optional<TypeDeclarer> et) {
        if (lit instanceof IntegerLiteral il) return write(il);
        if (lit instanceof NilLiteral nl) {
            if (et.has() && et.get() instanceof ArrayTypeDeclarer)
                return write("{}");
            return write(nl);
        }
        if (lit instanceof StringLiteral sl) return write(sl);
        return write(lit.toString());
    }

    private ExprWriter write(IntegerLiteral e) {
        switch (e.radix()) {
            case HEX -> write("0x");
            case OCT -> write("0");
            case BIN -> write("0b");
        }
        write(e.toString());
        if (e.value().bitLength() >= 64) {
            write("ULL");
        }
        return this;
    }

    private ExprWriter write(NilLiteral e) {
        return write("NULL");
    }

    private ExprWriter write(StringLiteral e) {
        return literalString(e);
    }

    // ===================================================================
    //  表达式大分发（逐节点发射）
    // ===================================================================

    public ExprWriter write(Expression e) {
        return switch (e) {
            case BinaryExpression ee -> write(ee);
            case UnaryExpression ee -> write(ee);
            case LiteralExpression ee -> write(ee);
            case VariableExpression ee -> write(ee);
            case SymbolExpression ee -> write(ee);
            case CallExpression ee -> write(ee);
            case NewExpression ee -> visitNew(ee);
            case ArrayExpression ee -> write(ee);
            case ObjectExpression ee -> write(ee);
            case MemberOfExpression ee -> write(ee);
            case IndexOfExpression ee -> write(ee);
            case ConvertExpression ee -> write(ee);
            case CheckNilExpression ee -> write(ee);
            case ReferEqualExpression ee -> write(ee);
            case ConditionalExpression ee -> write(ee);
            case BlockExpression ee -> write(ee);
            case ParenExpression ee -> write(ee);
            case DereferExpression ee -> write(ee);
            case CurrentExpression ee -> write(ee);
            case MethodExpression ee -> write(ee);
            case EnumValueExpression ee -> write(ee);
            case EnumIdExpression ee -> write(ee);
            case IsExpression ee -> write(ee);
            case TupleExpression ee -> write(ee);
            case TupleIndexExpression ee -> write(ee);
            case FunctionExpression ee -> write(ee);
            case AddressOfException ee -> write(ee);
            case PairsExpression ee -> ErrorUtil.unsupported("pairs");
            case null, default -> ErrorUtil.unreachable();
        };
    }

    // ---- 二元 / 一元运算 ----

    private ExprWriter write(BinaryExpression e) {
        var op = e.operator();
        // ReleaserBuilder 生成的表达式无 resultType（dump 前置 pass AST）：缺失时跳过
        // 需要类型的优化分支（类运算符重载 / memcmp / pow），直接走 cBinOp。
        var ltOpt = e.left().resultType.get();
        if (ltOpt.has()) {
            var lt = ltOpt.get();
            // 类运算符重载 → 直调宏方法（ClassMeta.methodSymbol 符号公式）
            if (lt instanceof DerivedTypeDeclarer dtd
                    && dtd.def() instanceof ClassDefinition lc) {
                var owner = lc;
                var cm = owner.binaryOperators().get(op);
                while (cm == null && owner.parent().has()) {
                    owner = owner.parent().must();
                    cm = owner.binaryOperators().get(op);
                }
                if (cm != null)
                    return writeOperatorCall(owner, cm, List.of(e.left(), e.right()));
            }
            if ((op == BinaryOperator.EQ || op == BinaryOperator.NE)
                    && lt.maybeRefer().none()
                    && !(lt instanceof PrimitiveTypeDeclarer)
                    && !(lt instanceof EnumTypeDeclarer)) {
                // 值类型 struct/class 比较 → memcmp
                write("memcmp(&(").write(e.left()).write("), &(");
                write(e.right()).write("), sizeof(").writeType(lt).write("))");
                if (op == BinaryOperator.EQ) write(" == 0");
                else write(" != 0");
                return this;
            }
            if (op == BinaryOperator.POW) {
                // Feng ^ 运算符 → C pow() 函数
                return write("pow((").write(e.left()).write("),(")
                        .write(e.right()).write("))");
            }
        }
        var sop = cBinOp(op);
        write('(').write(e.left()).write(')');
        write(' ').write(sop).write(' ');
        write('(').write(e.right()).write(')');
        return this;
    }

    private ExprWriter write(UnaryExpression e) {
        var op = e.operator();
        var ot = e.operand().resultType.must();
        // 类运算符重载 → 直调宏方法
        if (ot instanceof DerivedTypeDeclarer dtd
                && dtd.def() instanceof ClassDefinition oc) {
            var owner = oc;
            var cm = owner.unaryOperators().get(op);
            while (cm == null && owner.parent().has()) {
                owner = owner.parent().must();
                cm = owner.unaryOperators().get(op);
            }
            if (cm != null)
                return writeOperatorCall(owner, cm, List.of(e.operand()));
        }
        switch (op) {
            case NEGATIVE -> write('-');
            case INVERT -> {
                if (e.resultType.must() instanceof PrimitiveTypeDeclarer ptd
                        && ptd.primitive() == Primitive.BOOL)
                    write('!');
                else write('~');
            }
        }
        write('(').write(e.operand()).write(')');
        return this;
    }

    /**
     * 类运算符宏直调：{@code ({ $Class _op0 = (v); $Class$macro$op(&_op0); })}。
     * 符号名用 {@link ClassMeta#methodSymbol}（运算符 name 已是 macro id）。
     */
    private ExprWriter writeOperatorCall(
            ClassDefinition cd, ClassMethod cm, List<Expression> operands) {
        write("({ ");
        var args = new ArrayList<String>(operands.size());
        for (int i = 0; i < operands.size(); i++) {
            var a = operands.get(i);
            var isRef = a.resultType.must().maybeRefer().has();
            var n = "_op" + i;
            args.add(isRef ? n : "&" + n);
            write(cd.symbol());
            if (isRef) write('*');
            write(' ').write(n).write(" = (").write(a).write("); ");
        }
        write(ClassMeta.methodSymbol(cd, cm.name())).write('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) write(COMMA);
            write(args.get(i));
        }
        return write("); })");
    }

    private String cBinOp(BinaryOperator op) {
        return switch (op) {
            case MUL -> "*";
            case DIV -> "/";
            case MOD -> "%";
            case ADD -> "+";
            case SUB -> "-";
            case LSHIFT -> "<<";
            case RSHIFT -> ">>";
            case BITAND -> "&";
            case BITXOR -> "^";
            case BITOR -> "|";
            case EQ -> "==";
            case NE -> "!=";
            case GT -> ">";
            case LT -> "<";
            case GE -> ">=";
            case LE -> "<=";
            case AND -> "&&";
            case OR -> "||";
            case POW -> ErrorUtil.unreachable();
        };
    }

    // ---- 变量 / 符号 / 函数引用 ----

    private ExprWriter write(VariableExpression e) {
        return varName(e.variable());
    }

    private ExprWriter write(SymbolExpression e) {
        if (e.generic().isEmpty()) {
            return write(e.symbol());
        }
        // 泛型调用点已由 mono2 具体化（generic 应为空）；保留防御分支：mangle 名
        return write(Mangle.symbol(e.symbol(), e.generic()));
    }

    private ExprWriter write(FunctionExpression e) {
        if (e.generic().isEmpty()) {
            return write(e.symbol());
        }
        return write(Mangle.symbol(e.symbol(), e.generic()));
    }

    private ExprWriter write(AddressOfException e) {
        return write("&").write(e.subject());
    }

    private ExprWriter write(LiteralExpression e) {
        return writeLit(e.literal(), e.expectType.get());
    }

    /**
     * 变量 C 名：全局变量用符号，局部用 {@code name_id}。
     */
    ExprWriter varName(org.cossbow.feng.ast.dcl.Variable v) {
        if (v instanceof org.cossbow.feng.ast.GlobalVariable gv) {
            write(gv.symbol());
        } else {
            write(v.name());
        }
        return write('_').write(v.id());
    }

    // ---- 数组 / 元组 / 对象字面量 ----

    private ExprWriter write(ArrayExpression e) {
        // 类型化复合字面量——裸 {..} 只在声明初始化器里合法，return/块表达式上下文必须带类型
        var at = (ArrayTypeDeclarer) e.resultType.must();
        write('(').writeType(at).write("){{");
        var types = new RepeatList<>(at.element(), e.size());
        writeValues(e.elements(), types);
        return write("}}");
    }

    private ExprWriter write(TupleExpression e) {
        var resultType = (TupleTypeDeclarer) e.resultType.must();
        write('(').writeType(resultType).write("){");
        var i = 0;
        for (var elem : e.elements()) {
            if (i > 0) write(COMMA);
            writeValue(elem, resultType.get(i));
            i++;
        }
        return write('}');
    }

    private ExprWriter write(TupleIndexExpression e) {
        return write('(').write(e.subject()).write(").v").write(e.index());
    }

    private ExprWriter write(ObjectExpression oe) {
        var dt = oe.dtd();
        // 泛型具体化：模板类 DerivedType.def() 未更新为具体化类，字段类型残留
        // 泛型参数（如 [*]E / T）；须经 classMetas 取具体化类的字段类型。
        var def = concreteClassDef(dt.derivedType(), dt.def());
        var cd = def instanceof ClassDefinition c ? c : null;
        var sd = def instanceof StructureDefinition s ? s : null;
        var allFields = cd != null ? cd.allFields().values()
                : sd.fields();
        // 匿名 struct/union：省略类型前缀——C 可从上下文推断
        if (sd != null && sd.anonymous()) {
            write('{');
        } else {
            write('(').writeType(dt).write(')').write('{');
        }
        // 非 final 类值类型：置 $meta 供虚派发
        if (cd != null && !cd.isFinal()) {
            write(".$meta = ");
            writeMetaBaseRef(cd, dt.derivedType());
        }
        var data = new ArrayList<org.cossbow.feng.util.Groups.G2<Identifier, Expression>>();
        for (var f : allFields) {
            var o = oe.entries().tryGet(f.name());
            if (o.has()) data.add(org.cossbow.feng.util.Groups.g2(f.name(), o.get()));
        }
        if (cd != null && !cd.isFinal() && !data.isEmpty())
            write(COMMA);
        joinByComma(data, g -> {
            write('.');
            if (sd != null && sd.cType()) write(g.a().value());
            else write(g.a());
            write('=');
            // 用 writeValue 正确转换到字段类型
            var fieldType = cd != null ? cd.allFields().tryGet(g.a())
                    .map(org.cossbow.feng.ast.Field::type)
                    : sd != null ? sd.fields().tryGet(g.a())
                    .map(org.cossbow.feng.ast.Field::type)
                      : Optional.<TypeDeclarer>empty();
            if (fieldType.has()) writeValue(g.b(), fieldType.get());
            else write(g.b());
        });
        return write('}');
    }

    private ExprWriter joinByComma(
            List<org.cossbow.feng.util.Groups.G2<Identifier, Expression>> data,
            java.util.function.Consumer<org.cossbow.feng.util.Groups.G2<Identifier, Expression>> user) {
        for (int i = 0; i < data.size(); i++) {
            if (i > 0) write(COMMA);
            user.accept(data.get(i));
        }
        return this;
    }

    // ---- 成员访问 / 索引 ----

    private ExprWriter write(MemberOfExpression e) {
        var tdOpt = e.subject().resultType.get();
        if (tdOpt.has()) {
            var td = tdOpt.get();
            if (td instanceof EnumTypeDeclarer etd) return enumMember(e, etd.def());
            if (td instanceof DerivedTypeDeclarer dtd && dtd.def() instanceof EnumDefinition ed)
                return enumMember(e, ed);
            // tuple 成员：C 字段名是 v<i>（无 $ 前缀）——write(Identifier) 会给
            // 非 unnamed 标识符加 $，必须裸写（tuple.c 报 .$v0 no member 'v0'）。
            if (td instanceof TupleTypeDeclarer) {
                return ofMember(e.subject()).write(e.member().value());
            }
            // 数组成员访问：结果 cast（ReleaserBuilder 生成的访问无 resultType，跳过）
            if (td instanceof ArrayTypeDeclarer) {
                e.resultType.get().use(rt -> write('(').writeType(rt).write(')'));
            }
            // sync var 字段 → Feng$load_sl（锁读 + inc）
            if (isSyncVarField(e)) {
                write("((").baseTypeSymbol(e.resultType.must())
                        .write(" *)Feng$load_sl((void**)&");
                ofMember(e.subject());
                write(e.member());
                return write("))");
            }
        }
        ofMember(e.subject());
        return write(e.member());
    }

    private ExprWriter enumMember(MemberOfExpression e, EnumDefinition ed) {
        if (EnumDefinition.TokenFieldId.equals(e.member().value()))
            return write(e.subject());
        var mid = e.member().value();
        if ("name".equals(mid)) {
            // PRef → SRef：(Feng$ArraySRef_Byte){Feng$inc((void*)data), len}
            return write("(Feng$ArraySRef_Byte){Feng$inc((void*)")
                    .enumName(ed).write('[').write(e.subject()).write("].$name.$values), ")
                    .enumName(ed).write('[').write(e.subject()).write("].$name.$length}");
        }
        return enumName(ed).write('[').write(e.subject()).write("].").write(e.member());
    }

    private ExprWriter enumName(EnumDefinition ed) {
        return write("Feng$Enum_").write(ed.symbol());
    }

    /**
     * subject 访问：数组/值类型用 {@code .}，引用用 {@code ->}。
     */
    private ExprWriter ofMember(Expression subject) {
        write(subject);
        // cleanup 参数 p 是 C 层槽位指针（结构体值的 AST 类型，见 cleanupSlotPtr）；
        // ReleaserBuilder 用 SymbolExpression "p" 建模槽位参数，按符号名判定
        if (cleanupSlotPtr && subject instanceof SymbolExpression se
                && "p".equals(se.symbol().name().value()))
            return write("->");
        var tdOpt = subject.resultType.get();
        if (tdOpt.none() || tdOpt.get() instanceof ArrayTypeDeclarer
                || tdOpt.get().maybeRefer().none())
            return write('.');
        return write("->");
    }

    private ExprWriter write(IndexOfExpression e) {
        var stOpt = e.subject().resultType.get();
        // ReleaserBuilder 生成的无类型访问（subject 是数据指针，如 cleanup 的
        // p.$values）：裸下标 `values[i]`，无 checkIndex（循环索引已知有界）。
        if (stOpt.none()) {
            write(e.subject()).write('[').write(e.index()).write(']');
            return this;
        }
        var st = stOpt.get();
        write(e.subject());
        write(".$values[Feng$checkIndex(").write(e.index()).write(',');
        if (st instanceof ArrayTypeDeclarer atd && atd.refer().none()) {
            write(atd.len());
        } else {
            // 动态数组引用：运行时 $length
            write(e.subject()).write(".$length");
        }
        write(", (Uint64)(uintptr_t)&&_feng_fn_label, ");
        write(e.pos().start() != null ? e.pos().start().getLine() : 0);
        return write(")]");
    }

    // ---- 转换 / 判空 / 引用比较 ----

    private ExprWriter write(ConvertExpression e) {
        return write('(').write(e.primitive()).write(")(").write(e.operand()).write(')');
    }

    private ExprWriter write(CheckNilExpression e) {
        if (e.nil()) write('!');
        write('(').write(e.subject());
        // 数组引用结构体：判空检查数据指针
        if (e.subject().resultType.must() instanceof ArrayTypeDeclarer)
            write(".$values");
        return write(')');
    }

    private ExprWriter write(ReferEqualExpression e) {
        write(e.left());
        var lt = e.left().resultType.must();
        if (lt instanceof ArrayTypeDeclarer) write(".$values");
        write(e.same() ? " == " : " != ");
        write(e.right());
        var rt = e.right().resultType.must();
        if (rt instanceof ArrayTypeDeclarer) write(".$values");
        return this;
    }

    // ---- 条件 / 块 / 括号 / 解引用 ----

    private ExprWriter write(ConditionalExpression e) {
        write(e.condition()).write(" ? ");
        var rt = e.resultType.must();
        var isRef = rt.maybeRefer().has();
        if (isRef) write('(').writeType(rt).write(')');
        write(e.yes());
        write(" : ");
        if (isRef) write('(').writeType(rt).write(')');
        write(e.not());
        return this;
    }

    private ExprWriter write(BlockExpression e) {
        write("({").indent();
        for (var s : e.block()) context.stmts.write(s);
        var rt = e.result().resultType.getOrElse(e.resultType);
        writeValue(e.result(), rt.must()).endStmt();
        return dedent().write("})");
    }

    private ExprWriter write(ParenExpression e) {
        write('(');
        write(e.child());
        return write(')');
    }

    private ExprWriter write(DereferExpression e) {
        return write("(*").write(e.subject()).write(')');
    }

    // ---- 枚举 ----

    private ExprWriter write(EnumValueExpression e) {
        return write(e.value().id());
    }

    private ExprWriter write(EnumIdExpression e) {
        var t = e.index().resultType.must();
        return write("Feng$checkIndex(").write(e.index())
                .write(',').write('(').writeType(t).write(')')
                .write(e.def().size())
                .write(", (Uint64)(uintptr_t)&&_feng_fn_label, ")
                .write(e.pos().start() != null ? e.pos().start().getLine() : 0)
                .write(')');
    }

    // ---- RTTI / this / 方法引用 ----

    private ExprWriter write(IsExpression e) {
        if (!e.needCheck()) {
            // 编译期安全的 upcast → 直接 cast
            return castRef(e.subject(), e.type());
        }
        // 运行时 RTTI 检查
        var dst = e.type();
        var def = dst.def();
        if (def instanceof InterfaceDefinition iface) {
            var needInc = dst.checkRefer(ReferKind.STRONG) && !e.unbound();
            write("({ void* _s = (void*)(").write(e.subject()).write("); ");
            if (needInc) write(incFn(dst)).write('(');
            write("((Feng$iface_vtable(*(Feng$Meta**)_s,");
            if (!dst.derivedType().generic().isEmpty()) {
                write("&Feng$meta_").write(Mangle.name(dst.derivedType())).write(".base");
            } else {
                write("&Feng$meta_").write(iface.symbol()).write(".base");
            }
            write(")) ? _s : NULL)");
            if (needInc) write(')');
            return write("; })");
        }
        // 类层级检查：块表达式求值一次 subject
        var subjIsIface = e.subject().resultType.match(t ->
                t instanceof DerivedTypeDeclarer dtd && dtd.def() instanceof InterfaceDefinition);
        var needInc = dst.checkRefer(ReferKind.STRONG) && !e.unbound();
        write("({ void* _s = (void*)(").write(e.subject()).write("); ");
        if (needInc) write(incFn(dst)).write('(');
        write("((Feng$is_kind(");
        if (subjIsIface) write("*(Feng$Meta**)_s");
        else write("(($Object*)_s)->$meta");
        write(",");
        writeMetaBaseRef((ClassDefinition) def, dst.derivedType());
        write(")) ? (").writeType(dst).write(")_s : NULL)");
        if (needInc) write(')');
        return write("; })");
    }

    private ExprWriter write(CurrentExpression e) {
        return write("$self");
    }

    private ExprWriter write(MethodExpression e) {
        ofMember(e.subject());
        write(e.method().name());
        if (!e.generic().isEmpty()) {
            write('_').write(e.generic().stream()
                    .map(Mangle::typeKey)
                    .collect(Collectors.joining("_")));
        }
        return this;
    }

    // ---- render：表达式 → C 字符串（用于组合复合 lvalue） ----

    String render(Expression e) {
        var saved = out;
        var sb = new StringBuilder();
        out = sb;
        try {
            write(e);
        } finally {
            out = saved;
        }
        return sb.toString();
    }

    // ===================================================================
    //  调用发射 write(CallExpression) —— 方法/函数调用
    // ===================================================================

    static final Map<Identifier, String> ArrayMethods = Map.of(
            ArrayTypeDeclarer.MethodSwap.name(), "FENG$SWAP",
            ArrayTypeDeclarer.MethodMove.name(), "FENG$MOVE"
    );

    private ExprWriter arrayCall(CallExpression e,
                                 MethodExpression me,
                                 ArrayTypeDeclarer atd) {
        var mn = ArrayMethods.get(me.method().name());
        assert mn != null;
        // move 覆盖目标槽：先释放旧目标元素（宏只清零源槽）
        if (mn.equals("FENG$MOVE") && needsDestroy(atd.element())
                && e.arguments().size() == 2) {
            var subj = render(me.subject());
            var j = render(e.arguments().get(1));
            write("({ ");
            writeValueDestroy(atd.element(), subj + ".$values[" + j + "]", 0, false);
            write(" FENG$MOVE(").write(me.subject());
            for (var a : e.arguments()) write(COMMA).write(a);
            return write("); })");
        }
        write(mn).write('(').write(me.subject());
        for (var a : e.arguments()) write(COMMA).write(a);
        return write(')');
    }

    private ExprWriter write(CallExpression e) {
        if (e.callee() instanceof MethodExpression me) {
            var tdOpt = me.subject().resultType.get();
            // ReleaserBuilder 生成的内置调用（callee 为 SymbolExpression）不产生
            // MethodExpression 分支；这里 subject 无 resultType 时按直调处理。
            if (tdOpt.none()) {
                write(me.method().name());
                return writeCallArgs(e);
            }
            var td = tdOpt.get();
            // 数组内建方法 swap/move → Header.h 宏
            if (td instanceof ArrayTypeDeclarer atd) {
                return arrayCall(e, me, atd);
            }
            if (td instanceof DerivedTypeDeclarer dtd) {
                var def = dtd.def();

                // 方法级泛型：始终直调（不在任何虚表）
                if (!me.generic().isEmpty()) {
                    var classDt = dtd.derivedType();
                    var resolvedMethodArgs = me.generic().stream()
                            .map(Mangle::typeKey).toList();
                    var ownerDt = classDt;
                    if (def instanceof ClassDefinition cd) {
                        var owner = cd;
                        while (!owner.methods().exists(me.method().name())
                                && owner.parent().has()
                                && owner.parent().must() != ClassDefinition.ObjectClass) {
                            owner = owner.parent().must();
                        }
                        if (owner != cd) {
                            // owner 可能是泛型模板（map 定义在 SealedBox`X`）：
                            // ancestorDt 对泛型祖先退回调用点类型（IntBox），
                            // 拼出 IntBox$map_Float 而定义是 SealedBox_Int$map_Float。
                            // 沿调用点具体化 inherit 链找 owner 的 DerivedType
                            // （SealedBox`int` → mangle SealedBox_Int）。
                            ownerDt = concreteAncestorDt(cd, classDt, owner);
                        }
                    }
                    // 直调名：<ownerMangled>$<method>_<typeKey...>
                    // 用裸 value 拼（write(Identifier) 会给非 unnamed 标识符加 $ 前缀，
                    // 与 ClassMeta.methodSymbol 的 "$" + value 公式不一致 → 出现 $$map_Bool）
                    mangledName(ownerDt).write('$').write(me.method().name().value())
                            .write('_').write(String.join("_", resolvedMethodArgs));
                    write('(');
                    if (!ownerDt.equals(classDt))
                        write("(").mangledName(ownerDt).write(" *)");
                    if (td.maybeRefer().none()) write('&');
                    write(me.subject());
                    if (!e.arguments().isEmpty()) {
                        write(COMMA);
                        writeValues(e.arguments(), e.prototype().must().parameterSet().types());
                    }
                    write(')');
                    return this;
                }

                if (def instanceof InterfaceDefinition iface) {
                    // 接口派发：经对象 $meta 找接口虚表
                    write("((");
                    writeMetaType(dtd.derivedType());
                    write("*)Feng$iface_vtable(");
                    write("*(Feng$Meta**)");
                    write(me.subject());
                    write(",");
                    writeMetaBaseRef(iface, dtd.derivedType());
                    write("))->").write(me.method().name());
                } else if (def instanceof ClassDefinition cd && !cd.isFinal()
                        && td.maybeRefer().has()) {
                    // 非 final 类强引用 → 虚派发。扁平前缀布局：本类 meta 含全部
                    // 槽位（父槽在前 + override 原位替换），cast 成 subject 静态类型
                    // 后 ->m 直达任意槽（含继承槽与 override 槽），无需定位定义类。
                    var mName = me.method().name();
                    if (!isDynamicSlot(dtd.derivedType(), cd, mName)) {
                        // 宏方法（operator/index）不入虚表 → 直调；owner 用具体化类
                        // （cd 可能是模板，如 Vector`E` → Vector_A）：否则拼出
                        // Vector$feng$macro$index$get 而定义是 Vector_A$...。
                        var macroOwner = vtableOf(dtd.derivedType())
                                .map(VTable::def).getOrElse(cd);
                        write(methodSymbolOf(macroOwner, mName));
                        write('(').write(me.subject());
                        if (!e.arguments().isEmpty()) {
                            write(COMMA);
                            writeValues(e.arguments(), e.prototype().must().parameterSet().types());
                        }
                        return write(')');
                    }
                    write("((Feng$Meta_").mangledName(dtd.derivedType()).write("*)");
                    write(me.subject());
                    write("->$meta)->").write(mName);
                } else {
                    // 直调（final 类 / 值类型 subject）：owner 必须用具体化类
                    // （def 可能是模板类，如 Box`T`），否则拼出 Box$get 而非
                    // Box_Int$get；符号唯一权威 ClassMeta.methods()。
                    org.cossbow.feng.ast.oop.ClassDefinition owner = null;
                    if (def instanceof ClassDefinition c2) {
                        owner = vtableOf(dtd.derivedType())
                                .map(VTable::def).getOrElse(c2);
                    }
                    if (owner == null && me.method() instanceof ClassMethod cmm
                            && cmm.master() != null) {
                        owner = cmm.master();
                    }
                    if (owner != null) {
                        write(methodSymbolOf(owner, me.method().name()));
                    } else {
                        write(me.method().name());
                    }
                }
            } else {
                write(me.method().name());
            }
            write('(');
            // self：值类型取地址
            if (td.maybeRefer().none()) write('&');
            write(me.subject());
            if (!e.arguments().isEmpty()) {
                write(COMMA);
                writeValues(e.arguments(), e.prototype().must().parameterSet().types());
            }
            write(')');
            return this;
        }
        write(e.callee()).write('(');
        return writeCallArgs(e);
    }

    /**
     * 调用参数发射（ReleaserBuilder 生成的内置调用无 prototype 时容错）：
     * prototype 缺失 → 参数按自身 resultType 直写（无则裸写）。
     */
    private ExprWriter writeCallArgs(CallExpression e) {
        var proto = e.prototype();
        if (proto.has()) {
            writeValues(e.arguments(), proto.must().parameterSet().types());
            return write(')');
        }
        for (int i = 0; i < e.arguments().size(); i++) {
            if (i > 0) write(COMMA);
            var a = e.arguments().get(i);
            var at = a.resultType.get();
            if (at.has()) writeValue(a, at.get());
            else write(a);
        }
        return write(')');
    }

    // ===================================================================
    //  visitNew —— new 表达式
    // ===================================================================

    private ExprWriter visitNew(NewExpression e) {
        return switch (e.type()) {
            case NewDefinedType t -> visitNewDefined(t, e);
            case NewArrayType t -> visitNewArray(t, e);
            case null, default -> ErrorUtil.unreachable();
        };
    }

    private ExprWriter visitNewDefined(NewDefinedType ndt, NewExpression e) {
        var def = resolveNewDef(ndt, findType(ndt.type()));
        var nonFinal = def instanceof ClassDefinition cd && !cd.isFinal();
        var isBuiltin = def.builtin();
        var dt = def.link(e.pos());
        var et = new DerivedTypeDeclarer(e.pos(), dt);
        e.arg().use(a -> {
            // new(Foo, {id=2}) → 块表达式：alloc + 赋值字段
            write("({ ").writeDefinedType(ndt.type()).write(" *_p = (")
                    .writeDefinedType(ndt.type()).write(" *)");
            if (nonFinal) {
                var cd = (ClassDefinition) def;
                write("Feng$newObject(sizeof(").writeDefinedType(ndt.type()).write("), ");
                if (isBuiltin) write("&Feng$meta_").write(cd.symbol());
                else writeMetaBaseRef(cd, (DerivedType) ndt.type());
                write(")");
            } else
                write("Feng$alloc(sizeof(").writeDefinedType(ndt.type()).write("))");
            write("; ");
            // 整体拷贝（struct 拷贝或引用拷贝 + inc）
            write("*_p = ").writeValue(a, et).endStmt();
            if (nonFinal) {
                // 整体拷贝覆盖了 $meta 指针（源值 $meta 可能 NULL 或不同类）；
                // 恢复新类型的 meta，使 vDestroy 派发到正确析构。
                var cd = (ClassDefinition) def;
                write("_p->$meta = ");
                if (isBuiltin) write("&Feng$meta_").write(cd.symbol());
                else writeMetaBaseRef(cd, (DerivedType) ndt.type());
                write(";").endStmt();
            }
            write("_p; })");
        }, () -> {
            if (nonFinal) {
                var cd = (ClassDefinition) def;
                write("({ ").writeDefinedType(ndt.type()).write(" *_p = (")
                        .writeDefinedType(ndt.type()).write(" *)Feng$newObject(sizeof(")
                        .writeDefinedType(ndt.type()).write("), ");
                if (isBuiltin) write("&Feng$meta_").write(cd.symbol());
                else writeMetaBaseRef(cd, (DerivedType) ndt.type());
                write("); _p; })");
            } else {
                write("((").writeDefinedType(ndt.type()).write(" *)Feng$alloc(sizeof(")
                        .writeDefinedType(ndt.type()).write(")))");
            }
        });
        return this;
    }

    private ExprWriter visitNewArray(NewArrayType t, NewExpression e) {
        var elemKey = Mangle.typeKey(t.element());

        e.arg().use(a -> {
            // 有初始化参数：块表达式
            write("({ Feng$ArraySRef_").write(elemKey)
                    .write(" _a = {(").writeType(t.element())
                    .write(" *)Feng$alloc(").write(t.length())
                    .write("*sizeof(").writeType(t.element()).write(")), ")
                    .write(t.length()).write("}; ");

            if (a instanceof ArrayExpression ae) {
                // 显式值初始化：多余元素截断
                int i = 0;
                for (var v : ae.elements()) {
                    write("if (").write(String.valueOf(i)).write(" < _a.$length) { ");
                    write("_a.$values[").write(String.valueOf(i)).write("] = ");
                    writeValue(v, t.element());
                    write("; } ");
                    i++;
                }
            } else {
                // 从另一数组拷贝：源求值一次
                var srcType = a.resultType.must();
                writeType(srcType).write(" _src = ").write(a).write("; ");
                var elemType = t.element();
                var isStrongRef = elemType.maybeRefer().match(r -> r.isKind(ReferKind.STRONG));

                if (srcType instanceof ArrayTypeDeclarer atd && atd.refer().none()) {
                    // 定长源：$values 内联，长度编译期已知
                    var srcLen = String.valueOf(atd.len());
                    if (isStrongRef) {
                        write("for (Int64 _i = 0; _i < _a.$length && _i < ").write(srcLen)
                                .write("; _i++) _a.$values[_i] = Feng$inc(_src.$values[_i]); ");
                    } else {
                        write("memcpy(_a.$values, _src.$values, ")
                                .write("(_a.$length < ").write(srcLen).write(" ? _a.$length : ").write(srcLen).write(") ")
                                .write("*sizeof(").writeType(elemType).write(")); ");
                    }
                } else {
                    // 动态源：$values 指针、$length 运行时字段
                    if (isStrongRef) {
                        write("for (Int64 _i = 0; _i < _a.$length && _i < _src.$length; _i++) ")
                                .write("_a.$values[_i] = Feng$inc(_src.$values[_i]); ");
                    } else {
                        write("memcpy(_a.$values, _src.$values, ")
                                .write("(_a.$length < _src.$length ? _a.$length : _src.$length) ")
                                .write("*sizeof(").writeType(elemType).write(")); ");
                    }
                }
            }

            write("_a; })");
        }, () -> {
            // 无参数：简单复合字面量
            write("(Feng$ArraySRef_").write(elemKey).write("){");
            write('(').writeType(t.element()).write(" *)Feng$alloc(");
            write(t.length()).write("*sizeof(").writeType(t.element()).write(")), ");
            write(t.length()).write('}');
        });

        return this;
    }

    // ===================================================================
    //  辅助：meta 引用 / 祖先解析 / 类型判定 / 值释放
    // ===================================================================

    /**
     * DefinedType 类型名（new 表达式用）：primitive / derived / generic 参数。
     */
    private ExprWriter writeDefinedType(org.cossbow.feng.ast.gen.DefinedType t) {
        return switch (t) {
            case PrimitiveType pt -> write(pt.primitive());
            case DerivedType dt -> dt.def() instanceof EnumDefinition
                    ? write(Primitive.INT) : writeType(dt);
            case GenericType gt -> write(gt.name());
            case null, default -> ErrorUtil.unreachable();
        };
    }

    /**
     * new 表达式的类型定义（primitive → 类型定义，derived → def()）。
     */
    private TypeDefinition findType(DefinedType dt) {
        if (dt instanceof PrimitiveType pt)
            return pt.primitive().type();
        return ((DerivedType) dt).def();
    }

    /**
     * 泛型具体化类的 def 查找：模板类 {@code DerivedType.def()} 未更新为具体化类，
     * 字段类型残留泛型参数（{@code Vector`A` → Vector_A} 的 {@code values} 是
     * {@code [*]A}，模板 Vector 的 {@code values} 是 {@code [*]E} → 残留
     * {@code Feng$ArraySRef_E}）。经 {@code classMetas} 命中具体化类则返回其 def。
     * 非泛型（generic 空）直接返回原 def。
     */
    private TypeDefinition concreteClassDef(DerivedType d, TypeDefinition def) {
        if (d.generic().isEmpty()) return def;
        var meta = context.table.classMetas.tryGet(Mangle.symbol(d));
        if (meta.has() && meta.get().def() instanceof ClassDefinition cd) {
            return cd;
        }
        return def;
    }

    /**
     * new 目标类型的定义：def 可能是模板类（DerivedType.def() 未更新为
     * 具体化类）——字段初始化须取具体化类的字段类型（Vector`A` → Vector_A
     * 的 values 是 [*]A，模板 Vector 的 values 是 [*]E → 残留
     * Feng$ArraySRef_E）。
     */
    private TypeDefinition resolveNewDef(NewDefinedType ndt,
                                         TypeDefinition def) {
        if (def instanceof EnumDefinition)
            return Primitive.INT.type();
        if (ndt.type() instanceof DerivedType d) {
            return concreteClassDef(d, def);
        }
        return def;
    }

    /**
     * mangle 名或符号名（链式写）：generic 空 → symbol，否则 {@code Mangle.name}。
     */
    private ExprWriter mangledName(DerivedType dt) {
        return writeType(dt);
    }

    /**
     * DerivedType 对应的 vtable：泛型具体化用 mangle 符号，否则原符号。
     * 本模块类必命中（VTableBuilder 全量覆盖非 final 类）；跨模块类不在本表
     * {@code vtables} 里，返回 empty（调用方自行退回 AST 判定）。
     */
    private Optional<VTable> vtableOf(DerivedType dt) {
        var sym = dt.generic().isEmpty() ? dt.symbol() : Mangle.symbol(dt);
        return context.table.vtables.tryGet(sym);
    }

    /**
     * 方法是否走虚派发槽：优先查 vtable slots（dynamic 方法已全量入槽）；
     * 跨模块类（无本表 vtable）退回 AST 父链按 {@code ClassMethod.dynamic()} 判定。
     */
    private boolean isDynamicSlot(DerivedType dt, ClassDefinition cd, Identifier name) {
        var vt = vtableOf(dt);
        if (vt.has()) return vt.get().slots().exists(name);
        var cur = cd;
        while (cur != null && cur != ClassDefinition.ObjectClass) {
            for (var cm : cur.methods()) {
                if (cm.name().equals(name)) return cm.dynamic();
            }
            if (cur.parent().none()) break;
            cur = cur.parent().must();
        }
        return false;
    }

    /**
     * 方法直调符号：优先取 {@code ClassMeta.methods()} 预解析符号（含 mono 方法级
     * 实例化），查不到（跨模块类）退回 {@code ClassMeta.methodSymbol} 公式——
     * 与 FuncWriter 函数定义符号一致。
     */
    private String methodSymbolOf(ClassDefinition owner, Identifier name) {
        var meta = context.table.classMetas.tryGet(owner.symbol());
        if (meta.has()) {
            var mf = meta.get().methods().tryGet(name);
            if (mf.has()) return mf.get().symbol();
        }
        return ClassMeta.methodSymbol(owner, name);
    }

    /**
     * 类 meta 常量引用（偏移 0 的 {@code Feng$Meta*}，前缀布局首字段）。
     * 统一 cast 形式：{@code (const Feng$Meta*)&Feng$meta_<key>}，等价于旧的
     * {@code &Feng$meta_X.base...} 链，且不依赖 parent() 链（具体化类 parent 未链接）。
     */
    private ExprWriter writeMetaBaseRef(ClassDefinition cd, DerivedType dt) {
        write("(const Feng$Meta*)&Feng$meta_");
        return mangledName(dt);
    }

    /**
     * 接口 meta base 引用：{@code &Feng$meta_<ifaceKey>.base}（接口 meta 首成员是 base）。
     */
    private ExprWriter writeMetaBaseRef(InterfaceDefinition iface, DerivedType dt) {
        write("&Feng$meta_");
        mangledName(dt);
        return write(".base");
    }

    /**
     * 类/接口 meta 类型名：{@code Feng$Meta_<key>}。
     */
    private ExprWriter writeMetaType(DerivedType dt) {
        write("Feng$Meta_");
        return mangledName(dt);
    }

    /**
     * 祖先的具体化 {@code DerivedType}（防御分支，正常 mono2 已具体化）。
     * 具体化类的 parent()/inherit().def() 未链接（mono2 已知限制），泛型祖先
     * 无法在此解析——退回调用点自身类型，避免 NPE。
     */
    private DerivedType ancestorDt(ClassDefinition cd, DerivedType dt, ClassDefinition anc) {
        if (anc == cd) return dt;
        if (anc.generic().isEmpty()) {
            var result = new DerivedType(anc.symbol().pos(), anc.symbol(), TypeArguments.EMPTY);
            result.def(anc);
            return result;
        }
        return dt;
    }

    /**
     * 沿调用点的具体化 {@code inherit()} 链，找定义 {@code anc}（模板类）对应的
     * 具体化 {@code DerivedType}（{@code SealedBox`X` → SealedBox`int`}）。
     * <p>用于方法级泛型直调符号：调用点 {@code IntBox} 继承
     * {@code SealedBox`int`}，方法 {@code map`G`} 定义在模板 {@code SealedBox`X`}
     * 上，实例化产物符号是 {@code SealedBox_Int$map_Float}——若退回调用点
     * 自身类型会拼出 {@code IntBox$map_Float}（undefined）。
     */
    private DerivedType concreteAncestorDt(ClassDefinition cd, DerivedType dt,
                                           ClassDefinition anc) {
        var cur = cd;
        var curDt = dt;
        while (cur != null && cur != ClassDefinition.ObjectClass) {
            if (cur == anc) return curDt;
            if (cur.inherit().none()) break;
            var idt = cur.inherit().must();
            if (!(idt.def() instanceof ClassDefinition p)) break;
            cur = p;
            curDt = idt;
        }
        // 兜底：沿 inherit 链没找到 → 退回调用点自身类型
        return ancestorDt(cd, dt, anc);
    }

    /**
     * 沿 parent 链判子类。
     */
    private boolean isSubclass(ClassDefinition child, ClassDefinition ancestor) {
        var cur = child;
        while (cur.parent().has()) {
            cur = cur.parent().must();
            if (cur == ancestor) return true;
        }
        return false;
    }

    /**
     * 类实现的所有接口（含继承/间接）。
     */
    private Set<InterfaceDefinition> allIfaces(ClassDefinition cd) {
        return cd.allImpls();
    }

    // ---- 值释放（FENG$MOVE 目标槽预释放用） ----

    private boolean needsDestroy(TypeDeclarer t) {
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
        if (td instanceof org.cossbow.feng.ast.dcl.FuncTypeDeclarer) return true;
        if (td instanceof org.cossbow.feng.ast.dcl.GenericTypeDeclarer) return true;
        if (td instanceof DerivedTypeDeclarer dtd && dtd.refer().none()
                && dtd.def() instanceof StructureDefinition) return true;
        return false;
    }

    private boolean isArraySRef(TypeDeclarer td) {
        return td instanceof ArrayTypeDeclarer atd
                && atd.refer().has()
                && !atd.refer().get().isKind(ReferKind.PHANTOM);
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
     * "Feng$dec" 或 "Feng$dec_ns"（按类型 sync 标记）。
     */
    private String decFn(TypeDeclarer t) {
        return t.markSync() ? "Feng$dec" : "Feng$dec_ns";
    }

    /**
     * "Feng$cleanup_free" 或 "Feng$cleanup_free_ns"（plain dec→free，无析构派发）。
     */
    private String cleanupFreeFn(TypeDeclarer t) {
        return t.markSync() ? "Feng$cleanup_free" : "Feng$cleanup_free_ns";
    }

    /**
     * 释放 lvalue 处的值（统一值类型规则，旧 660–725 迁移）。
     * {@code syncVarField}：sync var 字段走掩码/专用函数路径。
     */
    private void writeValueDestroy(TypeDeclarer ft, String lv, int depth, boolean syncVarField) {
        if (isLeafValue(ft)) return;
        if (isArraySRef(ft)) {
            var ek = Mangle.typeKey(((ArrayTypeDeclarer) ft).element());
            write("Feng$cleanup_arr_").write(ek).write("(&").write(lv).write(")").endStmt();
            return;
        }
        if (ft instanceof ArrayTypeDeclarer atd && atd.refer().none()) {
            var iv = "i" + depth;
            write("for (Int64 ").write(iv).write(" = 0; ").write(iv)
                    .write(" < ").write(atd.len()).write("; ").write(iv).write("++) {").indent();
            writeValueDestroy(atd.element(), lv + ".$values[" + iv + "]", depth + 1, false);
            dedent().write('}').newLine();
            return;
        }
        if (ft instanceof TupleTypeDeclarer ttd) {
            int i = 0;
            for (var et : ttd.elements()) {
                writeValueDestroy(et, lv + ".v" + i, depth, false);
                i++;
            }
            return;
        }
        if (ft instanceof DerivedTypeDeclarer dtd && dtd.def() instanceof ClassDefinition cd) {
            if (dtd.refer().none()) {
                // 内嵌类值 → 静态析构（无 dec/free）
                write("Feng$destroy_").mangledName(dtd.derivedType())
                        .write("(&").write(lv).write(")").endStmt();
                return;
            }
            if (dtd.refer().get().isKind(ReferKind.PHANTOM)) return;  // borrow
            if (cd.isFinal()) {
                if (syncVarField) {
                    // @Sync var final 字段：掩码锁位，dec，destroy，free
                    write("{ uintptr_t _raw = atomic_load((atomic_uintptr_t*)&").write(lv)
                            .write("); void* _p = (void*)(_raw & ~(uintptr_t)1); if (_p && ").write(decFn(ft))
                            .write("(_p)) { Feng$destroy_").mangledName(dtd.derivedType())
                            .write("(_p); Feng$free(_p); } ").write(lv).write(" = NULL; }").endStmt();
                } else {
                    write("if (").write(lv).write(" && ").write(decFn(ft)).write("(").write(lv)
                            .write(")) { Feng$destroy_").mangledName(dtd.derivedType())
                            .write("(").write(lv).write("); Feng$free(").write(lv).write("); }").endStmt();
                }
                return;
            }
            // 非 final 强引用 → 释放入口（虚派发）
            if (syncVarField) {
                write("Feng$cleanup_sfield((void**)&").write(lv).write(")").endStmt();
            } else {
                write("Feng$release").write(ft.markSync() ? "" : "_ns")
                        .write("((void**)&").write(lv).write(")").endStmt();
            }
            return;
        }
        // 剩余强引用：接口 → release（虚派发）；boxed → plain dec→free
        if (ft.maybeRefer().match(r -> r.isKind(ReferKind.STRONG))) {
            if (isClassLikeStrongRef(ft)) {
                write("Feng$release").write(ft.markSync() ? "" : "_ns")
                        .write("((void**)&").write(lv).write(")").endStmt();
            } else {
                write(cleanupFreeFn(ft)).write("(&").write(lv).write(")").endStmt();
            }
        }
    }
}
