package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.meta.ClassMeta;
import org.cossbow.feng.analysis.meta.VTableSlot;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.proc.PrototypeDefinition;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Optional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.cossbow.feng.ast.dcl.ReferKind.PHANTOM;
import static org.cossbow.feng.ast.dcl.ReferKind.STRONG;

public class TypeWriter extends CWriter<TypeWriter> {

    public TypeWriter(WriterContext context) {
        super(context);
    }

    // ===================================================================
    //  类型名发射 write(TypeDeclarer) —— refer 分支核心
    // ===================================================================

    /**
     * 类型名发射（refer 分支：值类型目标 → {@code T*}、引用类型目标 → {@code T**}）。
     * {@code insideStructBody} 用于 struct/union 体内字段类型（需要 {@code struct } 前缀）。
     */
    public TypeWriter write(TypeDeclarer td, boolean insideStructBody) {
        return switch (td) {
            case PrimitiveTypeDeclarer ee -> writePrimitive(ee);
            case ArrayTypeDeclarer ee -> writeArray(ee);
            case DerivedTypeDeclarer ee -> writeDerived(ee, insideStructBody);
            case FuncTypeDeclarer ee -> writeFunc(ee);
            case EnumTypeDeclarer ee -> write(Primitive.INT);
            case LiteralTypeDeclarer ee -> writeLiteral(ee);
            case GenericTypeDeclarer ee -> write(ee.param().name());
            case TupleTypeDeclarer ee -> writeTuple(ee);
            case VoidTypeDeclarer ee -> write("void");
            case null, default -> ErrorUtil.unreachable();
        };
    }

    /**
     * 非 struct body 上下文（绝大多数场景）。
     */
    public TypeWriter write(TypeDeclarer td) {
        return write(td, false);
    }

    private TypeWriter writePrimitive(PrimitiveTypeDeclarer td) {
        write(PrimitiveName.get(td.primitive()));
        if (td.refer().has()) write('*');
        return this;
    }

    private TypeWriter writeArray(ArrayTypeDeclarer td) {
        var ek = Mangle.typeKey(td.element());
        var r = td.refer();
        if (r.none()) {
            return write("Feng$Array_").write(ek).write('_').write(td.len());
        }
        if (r.get().isKind(PHANTOM)) {
            return write("Feng$ArrayPRef_").write(ek);
        }
        return write("Feng$ArraySRef_").write(ek);
    }

    /**
     * DerivedTypeDeclarer 的 refer 分支：
     * <ul>
     *   <li>接口 → {@code void} / {@code void*}（接口引用是 {@code void*}）；</li>
     *   <li>枚举 → {@code Int}（枚举在 C 层用 Int）；</li>
     *   <li>类 / 结构体（值类型目标）→ {@code T} / {@code T*}（refer 有 → 补一个 {@code *}）；</li>
     *   <li>引用类型目标（虚引用指向强引用槽位）→ {@code T**}——由调用方在
     *       {@code write(td)} 之后再补一层（如 ReleaserWriter 的 cleanup {@code p} 参数）。</li>
     * </ul>
     */
    private TypeWriter writeDerived(DerivedTypeDeclarer td, boolean insideStructBody) {
        var def = td.def();
        if (def instanceof InterfaceDefinition) {
            write("void");
            if (td.refer().has()) write('*');
            return this;
        }
        if (def instanceof EnumDefinition) {
            write(Primitive.INT);
            if (td.refer().has()) write('*');
            return this;
        }
        if (insideStructBody) {
            if (def instanceof StructureDefinition sd) {
                write(sd.domain().name).write(' ');
            } else {
                write("struct ");
            }
        }
        write(td.derivedType());
        if (td.refer().has()) write('*');
        return this;
    }

    private TypeWriter writeFunc(FuncTypeDeclarer td) {
        // NamedFuncTypeDeclarer：命名原型 → 函数声明形态 typedef（<ret> Name(params)），
        // 变量类型加 * 成函数指针；泛型具体化 → Feng$Proto_<key>（已是函数指针 typedef）。
        if (td instanceof NamedFuncTypeDeclarer nftd) {
            if (!nftd.derivedType().generic().isEmpty()) {
                return write("Feng$").write(Mangle.protoKey(td.prototype()));
            }
            // AnonFuncNormalizer 合成的匿名原型（符号名以 Proto 开头）走
            // Feng$Proto_<key> 形态（emitPrototypeTypedef 已按此发射 typedef）；
            // 用户命名原型（Callable 等）才走 Name* 形态。
            if (nftd.derivedType().symbol().name().value().startsWith("Proto")) {
                return write("Feng$").write(Mangle.protoKey(td.prototype()));
            }
            return write(nftd.derivedType()).write('*');
        }
        return write("Feng$").write(Mangle.protoKey(td.prototype()));
    }

    private TypeWriter writeLiteral(LiteralTypeDeclarer td) {
        if (td.isInteger()) return write(Primitive.INT);
        if (td.isFloat()) return write(Primitive.FLOAT64);
        if (td.isBool()) return write(Primitive.BOOL);
        if (td.literal() instanceof StringLiteral sl) {
            return write(sl.array(Optional.of(STRONG)));
        }
        return this;
    }

    private TypeWriter writeTuple(TupleTypeDeclarer td) {
        return write("Feng$").write(Mangle.typeKey(td));
    }

    /**
     * 符号名或 mangle 名（泛型具体化）。
     */
    public TypeWriter write(DerivedType dt) {
        if (dt.generic().isEmpty()) return write(dt.symbol());
        return write(Mangle.name(dt));
    }

    // ===================================================================
    //  前向 typedef
    // ===================================================================

    /**
     * [H] 顶层类型前向 typedef（enum / struct / interface / class）。
     * module 模式下仅 context.header 输出（source 通过 include 本模块 .h 可见）。
     */
    public void declareType() {
        if (context.table.module.has() && !context.header) return;
        writeComment("type declarations");
        for (var t : context.table.dagStructures) declareType(t);
        for (var t : context.table.dagInterfaces) declareType(t);
        for (var t : context.table.dagClasses) declareType(t);
        newLine();
    }

    void declareType(StructureDefinition def) {
        if (def.cType()) return; // C-imported struct: handled by bridge context.header
        write("typedef ").write(def.domain().name).write(' ').write(def.symbol())
                .write(' ').write(def.symbol()).endStmt();
    }

    void declareType(ClassDefinition def) {
        if (def.isFinal()) return; // final 类无前向（值嵌入走 needsCompleteStruct 补发）
        write("typedef struct ").write(def.symbol()).write(' ').write(def.symbol()).endStmt();
    }

    void declareType(InterfaceDefinition def) {
        if (!def.generic().isEmpty()) return; // generic: concrete only
        write("typedef struct Feng$Meta_").write(def.symbol())
                .write(" Feng$Meta_").write(def.symbol()).endStmt();
    }

    /**
     * [S] 具体 typedef 之前的所有 struct tag 前向声明（类 + 物化类型）。
     * 保证 FixedArray/Tuple typedef 引用类/结构体时 tag 可见；物化数组/元组的
     * typedef 名也在此前向——方法槽参数/返回值只要名字可见即可（C11 6.7.5.3p12：
     * 函数声明器的参数允许不完整类型），见 docs/issues.md Fix B。
     */
    public void declareConcreteStructForwards() {
        if (context.table.module.has() && !context.header) return;
        var done = new LinkedHashSet<String>();
        // monoAfter 各锚点下的物化类型（具体化类 / 定长数组 / SRef-PRef 数组 / 元组）
        for (var list : context.table.monoAfter.values()) {
            declareMonoForwards(list, done);
        }
        // monoHead 中的物化类型同样需要前向
        declareMonoForwards(context.table.monoHead, done);
        // dagClasses：declareType() 已发射非 final 类前向，这里只补 final 类
        // （final 是值类型，declareType 跳过；槽参数按值引用时同样只需名字）
        for (var cd : context.table.dagClasses) {
            if (cd.generic().isEmpty() && cd.isFinal()) {
                var m = ClassMeta.symbolName(cd.symbol());
                if (done.add(m)) {
                    write("typedef struct ").write(m).write(' ').write(m).endStmt();
                }
            }
        }
        if (!done.isEmpty()) newLine();
    }

    private void declareMonoForwards(List<TypeDefinition> defs, LinkedHashSet<String> done) {
        for (var def : defs) {
            var m = monoForwardName(def);
            if (m != null && done.add(m)) {
                write("typedef struct ").write(m).write(' ').write(m).endStmt();
            }
        }
    }

    /** 物化类型的前向 typedef 名；非物化结构（接口等）返回 null。 */
    private static String monoForwardName(TypeDefinition def) {
        if (def instanceof ClassDefinition cd) return ClassMeta.symbolName(cd.symbol());
        if (def instanceof FixedArrayDefinition fad) return fixedArrayName(fad);
        if (def instanceof ArrayRefDefinition ard) return arrayRefName(ard);
        if (def instanceof TupleDefinition td && td.elementTypes() != null) return tupleName(td);
        return null;
    }

    // ===================================================================
    //  具体类型 typedef（monoDepsByUnit 两遍结构）
    // ===================================================================

    private void declareFixedArray(FixedArrayDefinition fad) {
        var typeName = fixedArrayName(fad);
        write("typedef struct ").write(typeName).write(" { ");
        write(fad.elementType());
        write(" $values[").write(fad.length()).write("]; } ").write(typeName).endStmt();
    }

    /** 物化定长数组 typedef 名：{@code Feng$Array_<elemKey>_<len>}。 */
    private static String fixedArrayName(FixedArrayDefinition fad) {
        return "Feng$Array_" + Mangle.typeKey(fad.elementType()) + "_" + fad.length();
    }

    private void declareArrayRef(ArrayRefDefinition ard) {
        var typeName = arrayRefName(ard);
        write("typedef struct ").write(typeName).write(" { ");
        write(ard.elementType());
        write("* $values; Int64 $length; } ").write(typeName).endStmt();
    }

    /** 物化 SRef/PRef 数组 typedef 名：{@code Feng$ArraySRef_<elemKey>} / {@code Feng$ArrayPRef_<elemKey>}。 */
    private static String arrayRefName(ArrayRefDefinition ard) {
        var prefix = ard.phantom() ? "ArrayPRef" : "ArraySRef";
        return "Feng$" + prefix + "_" + Mangle.typeKey(ard.elementType());
    }

    private void declareTuple(TupleDefinition td) {
        if (td.elementTypes() == null) return;
        write("typedef struct ").write(tupleName(td)).write(" {").indent();
        int i = 0;
        for (var et : td.elementTypes()) {
            write(et).write(" v").write(i).endStmt();
            i++;
        }
        dedent().write("} ").write(tupleName(td)).endStmt();
    }

    /** 物化元组 typedef 名：{@code Feng$Tuple_<k0>_<k1>...}。 */
    private static String tupleName(TupleDefinition td) {
        return "Feng$Tuple_" + td.elementTypes().stream()
                .map(Mangle::typeKey).collect(Collectors.joining("_"));
    }

    private void declarePrototype(PrototypeDefinition pd) {
        var pt = pd.prototype();
        var key = Mangle.protoKey(pt);
        write("typedef ");
        pt.returnSet().use(this::write, () -> write("void"));
        write(" (*Feng$").write(key).write(")(");
        writeParamTypes(pt);
        write(')').endStmt();
    }

    /**
     * 参数类型列表（不含名字），以逗号分隔。
     */
    private void writeParamTypes(Prototype pt) {
        var first = true;
        for (var t : pt.parameterSet().types()) {
            if (!first) write(", ");
            first = false;
            write(t);
        }
    }

    // ===================================================================
    //  字符串池（enum 表引用 Feng$constString_<id>，必须先于 enum 发射）
    // ===================================================================

    /**
     * [S] 字符串字面量池：{@code static struct { Feng$Header context.header; struct { Byte $values[N]; } array; } Feng$constString_<id> = ...}
     * 仅 source（context.header 无字符串池；引用方通过 include 可见）。
     */
    public void literalStringCache() {
        if (context.header) return;
        writeComment("string cache");
        var list = context.table.stringCache.keySet().stream()
                .sorted(Comparator.comparingInt(StringLiteral::id)).toList();
        for (var sl : list) {
            write("static struct { Feng$Header header; struct { Byte $values[")
                    .write(sl.length()).write("]; } array; } ");
            literalString(sl);
            write(" = {{.refcnt = 1}, {{");
            for (byte b : sl.value()) write(b).write(',');
            write("}}}").endStmt();
        }
        newLine();
    }

    private TypeWriter literalString(StringLiteral sl) {
        return write("Feng$constString_").write(sl.id());
    }

    // ===================================================================
    //  enum 定义
    // ===================================================================

    /**
     * [S] 枚举值表：{@code static Feng$Enum Feng$Enum_<sym>[N] = { {val, {name.$values, len}}, ... }}。
     * 仅 source（Feng$Enum 结构在 Header.h，表定义放 .c）。
     */
    public void enumDefinition() {
        if (context.header) return;
        writeComment("enum definition");
        for (var ed : context.table.enumList) declareEnum(ed);
        newLine();
    }

    void declareEnum(EnumDefinition ed) {
        write("static Feng$Enum Feng$Enum_").write(ed.symbol()).write('[')
                .write(ed.size()).write("] = {").indent();
        for (var v : ed.values()) {
            write('{').write(v.val()).write(", {");
            literalString(v.nameLit()).write(".array.$values, ");
            write(v.nameLit().length()).write("}},").newLine();
        }
        dedent().write("}").endStmt();
    }

    // ===================================================================
    //  struct / union 定义
    // ===================================================================

    /**
     * [H] 结构体定义（module 模式下仅 context.header；source 通过 include 本模块 .h 可见）。
     */
    public void structureDefinition() {
        if (context.table.module.has() && !context.header) return;
        writeComment("structure definitions");
        for (var sd : context.table.dagStructures) writeStructure(sd);
        newLine();
    }

    /**
     * [H] 命名原型 typedef：{@code typedef <ret> <symbol>(<params>);}（函数声明形态，
     * 变量类型 {@code <symbol>*} 才是函数指针——见 {@link #writeFunc}）。
     * module 模式下仅 context.header；泛型原型只出具体化实例（monoDepsByUnit 已含）。
     */
    public void declareProtoTypedefs() {
        if (context.table.module.has() && !context.header) return;
        writeComment("prototype definition");
        context.table.dagPrototypes.bfs(pd -> {
            if (pd.prototype().hasTypeVar()) return; // 泛型模板：只出具体化

            // AnonFuncNormalizer 合成的匿名原型（符号名以 Proto 开头，如
            // func() → $Proto_Void）：字段引用走 Feng$Proto_<key> 形态
            // （writeFunc L159），必须用 emitPrototypeTypedef 发射，否则
            // prototype.feng 报 unknown type name 'Feng$Proto_Void'。
            if (pd.symbol().name().value().startsWith("Proto")) {
                declarePrototype(pd);
                declareAfter(pd);
                return;
            }
            write("typedef ");
            var pt = pd.prototype();
            pt.returnSet().use(this::write, () -> write("void"));
            write(' ').write(pd.symbol()).write('(');
            writeParamTypes(pt);
            write(')').endStmt();
            declareAfter(pd);
        });
        newLine();
    }

    private void writeStructure(StructureDefinition sd) {
        if (sd.cType() && !sd.anonymous()) {
            // 命名 C-imported：typedef 在 bridge 区发射，但锚定在其后的 mono
            // 定长数组/元组（如 [3]A）仍须在此发射，否则定义丢失（incomplete type）。
            declareAfter(sd);
            return;
        }

        var p = sd.pack();
        if (p > 0) {
            write("#pragma pack(push, ").write(p).write(')').newLine();
        }
        write("typedef ").write(sd.domain().name);
        if (sd.typeAlign() > 0) {
            write(" __attribute__((aligned(").write(sd.typeAlign()).write(")))");
        }
        write(' ').write(sd.symbol());
        write(" {").indent();
        for (var sf : sd.fields()) writeStructureField(sf);
        dedent().write("} ").write(sd.symbol()).endStmt();
        if (p > 0) {
            write("#pragma pack(pop)").newLine();
        }
        if (!sd.cType() && sd.layout().has()) {
            write("_Static_assert(sizeof(").write(sd.symbol())
                    .write(") == ").write(sd.layout().must().size())
                    .write(", \"size check\")");
            endStmt();
        }
        declareAfter(sd);
    }

    private void writeStructureField(StructureField sf) {
        write(sf.type(), true).write(' ').write(sf.name());
        if (sf.bitfield().has()) write(':').write(sf.bits());
        if (sf.align() > 0) {
            write(" __attribute__((aligned(").write(sf.align()).write(")))");
        }
        endStmt();
    }


    private void declareType(TypeDefinition def) {
        switch (def) {
            case ClassDefinition cd -> declareClass(cd);
            case StructureDefinition sd -> declareType(sd);
            case EnumDefinition ed -> declareEnum(ed);
            case InterfaceDefinition id -> declareInterface(id);
            case TupleDefinition td -> declareTuple(td);
            case FixedArrayDefinition ad -> declareFixedArray(ad);
            case ArrayRefDefinition ad -> declareArrayRef(ad);
            case PrototypeDefinition pd -> declarePrototype(pd);
            default -> ErrorUtil.unreachable();
        }
    }

    // ===================================================================
    //  类扁平字段布局（classesDefinition）
    // ===================================================================

    public void classesDefinition() {
        if (context.table.module.has() && !context.header) return;
        writeComment("class definition");
        for (var cd : context.table.dagClasses) {
            if (cd.generic().isEmpty()) declareClass(cd);
        }
        newLine();
    }

    public void interfaceDefinitions() {
        if (context.table.module.has() && !context.header) return;
        writeComment("interface definition");
        for (var id : context.table.dagInterfaces) {
            if (id.generic().isEmpty()) declareInterface(id);
        }
    }

    public void headDefinitions() {
        if (context.table.module.has() && !context.header) return;
        for (var def : context.table.monoHead) {
            declareType(def);
        }
    }

    private void declareAfter(Entity def) {
        var monos = context.table.monoAfter.get(def);
        if (monos == null) return;
        for (var td : monos) {
            declareType(td);
        }
    }

    private void declareClass(ClassDefinition cd) {
        if (cd.builtin()) return;
        if (context.table.module.has() && !context.header)
            return; // module 模式仅 context.header

        if (cd.isFinal()) {
            // final 类：无 $meta，直接 own fields
            write("typedef struct ").write(cd.symbol()).write(" {").indent();
            for (var f : cd.fields().values()) writeClassField(f);
            dedent().write("} ").write(cd.symbol()).endStmt();
        } else {
            // 非 final：$meta 首字段 + 扁平字段（父先子后，子类可 reinterpret 成父类）
            write("typedef struct ").write(cd.symbol()).write(" {").indent();
            write("const Feng$Meta* $meta").endStmt();
            writeFlatStructFields(cd);
            dedent().write("} ").write(cd.symbol()).endStmt();
            newLine();
            declareMetaType(cd);
        }
        newLine();
        declareAfter(cd);
    }

    /**
     * 非 final 类的 {@code Feng$Meta_<sym>} 类型声明（扁平布局）。
     *
     * <p>单继承无需 C++ 式嵌套 sub-object：首成员固定为运行时头 {@code Feng$Meta base}
     * （offset 0：instance_size / super / ifaces / destroy），父类查询走运行时
     * {@code Feng$Meta.super}、接口查询走 {@code ifaces}——父类 meta 类型不再被按值
     * 引用，无需递归声明；guard 仅防 header/source 双 pass 与跨模块重复 include。
     *
     * <p>方法槽单源 {@code VTable.slots()}（父槽在前、override 原位替换、新槽追加）：
     * 子类 meta 结构是父类的严格前缀扩展，派发端 {@code ->m} 直达任意槽。非 dynamic
     * 方法（运算符 / 索引宏）不入槽——它们是直调，不占 meta 空间。
     */
    private void declareMetaType(ClassDefinition cd) {
        var vtOpt = context.table.vtables.tryGet(cd.symbol());
        if (vtOpt.none()) return;
        var vt = vtOpt.get();

        // 内嵌接口（i<ifaceKey> 成员）按值嵌入，接口 meta struct 必须先定义——
        // 与旧 CGenerator declareClass 4294-4316 的「先 declareInterface 再输出 meta」一致。
        for (var ivt : vt.ifaces()) {
            declareInterface(ivt.iface());
        }
        write("typedef struct Feng$Meta_").write(cd.symbol()).write(" {").indent();
        write("Feng$Meta base").endStmt();
        for (var slot : vt.slots()) {
            writeClassMethodSlot(slot);
        }
        // 接口内嵌虚表（与 VTableBuilder 的 iface 列表一致；metaSymbol = "Feng$meta_<ifaceKey>"）
        for (var ivt : vt.ifaces()) {
            var iName = ivt.metaSymbol().substring("Feng$meta_".length());
            write("Feng$Meta_").write(iName).write(" i").write(iName).endStmt();
        }
        dedent().write("} Feng$Meta_").write(cd.symbol()).endStmt();
        // context.header 中 extern 声明 meta 常量（定义在 source，阶段 6 MetaWriter）
        if (context.header) {
            write("extern const Feng$Meta_").write(cd.symbol())
                    .write(" Feng$meta_").write(cd.symbol()).endStmt();
        }
    }

    /**
     * 接口 meta 类型：{@code Feng$Meta base} + 全部接口方法槽。
     */
    private void declareInterface(InterfaceDefinition id) {
        if (id.builtin()) return; // Header.h / Feng$Builtins.c 已有
        if (context.table.module.has() && !context.header) return;

        var guard = guardName("FENG_STRUCT_Feng$Meta_" + ClassMeta.symbolName(id.symbol()));
        write("#ifndef ").write(guard).newLine();
        write("#define ").write(guard).newLine();
        write("typedef struct Feng$Meta_").write(id.symbol()).write(" {").indent();
        write("Feng$Meta base").endStmt();
        for (var im : id.allMethods()) {
            writeMethodSlot(im.prototype(), im.name());
        }
        dedent().write("} Feng$Meta_").write(id.symbol()).endStmt();
        if (context.header) {
            write("extern const Feng$Meta_").write(id.symbol())
                    .write(" Feng$meta_").write(id.symbol()).endStmt();
        }
        write("#endif").newLine();
        newLine();
        declareAfter(id);
    }

    /**
     * 方法槽函数指针字段：{@code <ret> (*$name)(void* self, <params>);}（self 首参统一规则）。
     */
    private void writeMethodSlot(Prototype pt, Identifier name) {
        pt.returnSet().use(this::write, () -> write("void"));
        write(" (*").write(name).write(")(void* self");
        var ps = pt.parameterSet();
        if (!ps.isEmpty()) {
            write(", ");
            writeParamTypes(pt);
        }
        write(')').endStmt();
    }

    /**
     * 类方法槽函数指针字段：{@code <ret> (*$name)(<X>* self, <params>);}，X = meta 类。
     * 签名单源 {@link VTableSlot#signature()}
     * （= {@code MethodFunc.prototype()}）；唯一改写：self 类型固定为 **meta 自己的类**
     * （而非槽 owner）——派发端 {@code ((Feng$Meta_X*)x->$meta)->m(x)} 参数类型严格
     * 匹配（扁平前缀布局下静态父类型引用也成立），初始化器侧用 cast 消差
     * （MetaWriter.writeSlotCastType，与本方法同构）。
     */
    private void writeClassMethodSlot(VTableSlot slot) {
        var pt = slot.signature();
        context.funcs.writeSig(pt, () ->
                write("(*").write(slot.name()).write(')'));
        endStmt();
    }

    /**
     * 类方法槽参数列表（self 类型固定为 meta 自己的类 {@code X*}）：槽字段声明
     * （本类）与 MetaWriter 槽 cast 共用——两处类型恒同构。
     */
    void writeClassSlotParams(ClassDefinition cd, Prototype pt) {
        write(ClassMeta.symbolName(cd.symbol())).write('*');
        var ps = pt.parameterSet();
        for (int i = 1; i < ps.size(); i++) {   // 0 = SelfParameter（self 类型已重写）
            write(", ");
            write(ps.fixed(i).type());
        }
    }

    /**
     * 类字段：{@code <type> $name;}（struct body 上下文 → 结构体字段加 struct 前缀）。
     */
    private void writeClassField(ClassField cf) {
        write(cf.type(), true).write(' ').write(cf.name()).endStmt();
    }

    /**
     * 扁平字段布局：直接遍历 {@code cd.allFields()}。
     *
     * <p>allFields() 已按「继承字段在前、own 字段在后」排序（SemanticAnalyzer 对普通类、
     * mono2 {@code concretizeClass} 对具体化类都保证此顺序）——父字段在前、子类 own 在后，
     * 因此 {@code (Parent*)&child} 可 reinterpret。**不再递归 parent()**：具体化类的
     * {@code parent()}/{@code inherit().def()} 未链接（mono2 已知限制），而 allFields()
     * 的字段类型已全部具体化（普通类经 {@code gm.mapIf}、具体化类经 {@code concretizeClass}
     * 重建），直接写即可。跳过 Object 根字段：Object 无字段，allFields 不含；builtin 父类
     * （Exception）的 fn/line 字段保留，保证 reinterpret 兼容。
     */
    private void writeFlatStructFields(ClassDefinition cd) {
        for (var cf : cd.allFields().values()) {
            writeClassField(cf);
        }
    }

    private static String guardName(String name) {
        return name.replace('$', '_');
    }
}
