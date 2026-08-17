package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.meta.ClassMeta;
import org.cossbow.feng.analysis.meta.VTable;
import org.cossbow.feng.analysis.meta.VTableSlot;
import org.cossbow.feng.ast.Mangle;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.ast.proc.FixedParameter;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.ErrorUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * OOP 元数据层 writer（阶段 6）：把前置 pass（VTableBuilder）算好的 {@code vtables}
 * 直接 dump 成 C 常量定义——类 meta 常量 {@code Feng$meta_<Class>}、接口 meta 常量、
 * iface entries 数组。**纯发射，零分析**：虚派发 / 接口派发 / is 检查 / new / catch
 * 的调用点全部已在 ExprWriter / StmtWriter 处理完毕，它们只引用 {@code Feng$meta_<X>}
 * 常量符号；本 writer 不产生任何调用逻辑。
 *
 * <p>发射规则（对照旧 CGenerator.metaDefinitions 3763-3937 / emitIfaceVTables
 * 4119-4155）：
 * <ul>
 *   <li>类 meta = <b>扁平布局</b>（单继承无需 C++ 式嵌套 sub-object）：首成员运行时头
 *       {@code Feng$Meta base}（offset 0），全部方法槽平铺在同一层——初始化器
 *       {@code .base = { ... }, .<slot> = (cast)sym, ...}；父类查询走运行时
 *       {@code .super}（源 = {@link VTable#parent()}），接口查询走 {@code .ifaces}；</li>
 *   <li>槽位函数指针类型与 TypeWriter.writeClassMethodSlot 的字段声明同构
 *       （共用 {@link TypeWriter#writeClassSlotParams}）：self 固定为 meta 自己的类，
 *       槽值 cast 消除 override / 父槽符号的类型差；</li>
 *   <li>覆盖槽只改函数指针指向子类符号、不新增槽——{@code VTable.slots()} 已解析好
 *       最终符号，直接取 {@code slot.symbol()}；</li>
 *   <li>接口虚表是内嵌结构体 {@code i<ifaceKey>}，槽名来自接口 allMethods，
 *       槽值 cast 成接口方法签名（接口引用是 {@code void*}）；</li>
 *   <li>iface entries：{@code { (const Feng$Meta*)&Feng$meta_<ifaceKey>,
 *       offsetof(Feng$Meta_<Class>, i<ifaceKey>) }} + {@code { NULL, 0 }} 终止；</li>
 *   <li>{@code instance_size} = C {@code sizeof}（类扁平 struct），不入 VTable；</li>
 *   <li>destroy 符号唯一权威 = ReleaserBuilder 产物（{@code ClassMeta.destroy()}），
 *       cast 成 {@code void (*)(void*)} 消除 self 类型差。</li>
 * </ul>
 *
 * <p>纯发射器：vtables / dagInterfaces / monoDepsByUnit 均已由前置 pass 算好，
 * 无 lazy 注册、无 register-then-emit 状态（仅用 Set 去重避免重复发射，同
 * TypeWriter 的 done 集合）。
 */
public class MetaWriter extends CWriter<MetaWriter> {

    public MetaWriter(WriterContext context) {
        super(context);
    }

    // ===================================================================
    //  metaDefinitions —— 全部 [S]，source only
    // ===================================================================

    /**
     * [S] meta 常量定义（source only）：接口 meta → 具体化类 meta 前置声明 →
     * 类 meta 常量。module 模式下 extern 声明在 context.header（TypeWriter.declareMetaType /
     * declareInterface 已发 [H] extern），本方法只发定义。
     */
    public void metaDefinitions() {
        if (context.header) return;
        if (context.table.vtables.isEmpty() &&
                context.table.dagInterfaces.isEmpty()) return;

        // 1. 接口 meta 常量（类 meta 的 iface entries / 接口派发引用它们，必须先定义）
        interfaceMetas();

        // 2. 具体化类 meta 前置声明（非泛型类继承具体化类时，其 .super 引用
        //    Feng$meta_<mangled>，而具体化定义在后 → 先声明；旧 3853-3862）
        emitConcreteMetaForwards();

        // 3. 类 meta 常量（vtables 遍历序 = classMetas 构造序：非泛型在前、具体化在后）
        writeComment("class metadata");
        for (var vt : context.table.vtables) {
            classMeta(vt);
        }
        newLine();
    }

    // ===================================================================
    //  接口 meta 常量
    // ===================================================================

    /**
     * [S] 接口 meta 常量：{@code const Feng$Meta_<ifaceKey> Feng$meta_<ifaceKey> =
     * { .base = { .instance_size = 0, .super = NULL|祖先接口, .iface_count = 0,
     * .ifaces = NULL, .destroy = NULL } };}（旧 3774-3813）。
     *
     * <p>来源与 TypeWriter.classesDefinition 一致：dagInterfaces（非 builtin、
     * 非泛型）+ monoDepsByUnit / monoTrailing 中的 InterfaceDefinition（具体化，
     * symbol 已 mangle）。builtin 接口跳过（Header.h extern + Feng$Builtins.c 已定义）。
     */
    private void interfaceMetas() {
        var done = new HashSet<String>();
        boolean any = false;
        for (var id : context.table.dagInterfaces) {
            if (id.builtin() || !id.generic().isEmpty()) continue;
            any |= interfaceMeta(id, done);
        }
        for (var list : context.table.monoAfter.values()) {
            for (var def : list) {
                if (def instanceof InterfaceDefinition id) {
                    any |= interfaceMeta(id, done);
                }
            }
        }
        for (var def : context.table.monoHead) {
            if (def instanceof InterfaceDefinition id) {
                any |= interfaceMeta(id, done);
            }
        }
        if (any) newLine();
    }

    /**
     * 发射单个接口 meta 常量（已发过的 symbol 跳过，防跨 unit 重复）。
     */
    private boolean interfaceMeta(InterfaceDefinition id, Set<String> done) {
        var sym = ClassMeta.symbolName(id.symbol());
        if (!done.add(sym)) return false;
        writeComment("interface metadata " + sym);
        write("const Feng$Meta_").write(sym).write(" Feng$meta_").write(sym)
                .write(" = {").indent();
        write(".base = {").indent();
        write(".instance_size = 0,").newLine();
        // 接口继承（parts/supers）：.super 指向第一个祖先接口 meta 的 .base；
        // 具体化接口一律 NULL（旧 3806）
        if (id.generic().isEmpty() && !id.supers().isEmpty()) {
            var superDt = id.supers().getFirst();
            write(".super = (const Feng$Meta*)&Feng$meta_")
                    .write(Mangle.ifaceKey(superDt)).write(".base,").newLine();
        } else {
            write(".super = NULL,").newLine();
        }
        write(".iface_count = 0,").newLine();
        write(".ifaces = NULL,").newLine();
        write(".destroy = NULL").newLine();
        dedent().write("}").newLine();
        dedent().write("}").endStmt();
        newLine();
        return true;
    }

    // ===================================================================
    //  具体化类 meta 前置声明
    // ===================================================================

    /**
     * [S] 具体化类（vtables 中不属于 dagClasses 的条目）meta 前置声明：
     * {@code const Feng$Meta_<mName> Feng$meta_<mName>;}。非泛型类继承具体化
     * 泛型实例时（如 {@code class Foo : Base<Int>}），Foo 的 meta 常量引用
     * {@code Feng$meta_Base_Int}，必须先声明后定义（旧 3853-3862）。
     */
    private void emitConcreteMetaForwards() {
        var dagSyms = new HashSet<String>();
        for (var cd : context.table.dagClasses) {
            if (!cd.generic().isEmpty()) continue;
            dagSyms.add(ClassMeta.symbolName(cd.symbol()));
        }
        boolean any = false;
        for (var vt : context.table.vtables.values()) {
            var sym = ClassMeta.symbolName(vt.def().symbol());
            if (dagSyms.contains(sym)) continue; // 非泛型类，定义紧跟其后，无需前置
            if (!any) {
                writeComment("concrete class metadata forward declarations");
                any = true;
            }
            write("const Feng$Meta_").write(sym).write(" Feng$meta_").write(sym).endStmt();
        }
        if (any) newLine();
    }

    // ===================================================================
    //  类 meta 常量
    // ===================================================================

    /**
     * [S] 单个非 final 类的 meta 常量（扁平布局）：
     * {@code const Feng$Meta_<X> Feng$meta_<X> = { .base = {...}, .<slot> = (cast)sym,
     * ..., .i<iface> = {...} };}。
     *
     * <p>全部方法槽平铺一层（VTable.slots 扁平序 = struct 槽序，TypeWriter 同源），
     * 槽值 = VTableBuilder 已解析的符号（override → 子类实现）。destroy 符号唯一
     * 权威 = ReleaserBuilder 产物（{@code ClassMeta.destroy()}）。
     */
    private void classMeta(VTable vt) {
        var cd = vt.def();
        var sym = ClassMeta.symbolName(cd.symbol());

        // ① iface entries 数组（旧 3870-3884）
        if (!vt.ifaces().isEmpty()) {
            write("static const Feng$IfaceEntry Feng$meta_").write(sym)
                    .write("_ifaces[] = {").indent();
            for (var ivt : vt.ifaces()) {
                var iName = ivt.metaSymbol().substring("Feng$meta_".length());
                write("{ (const Feng$Meta*)&Feng$meta_").write(iName)
                        .write(", offsetof(Feng$Meta_").write(sym)
                        .write(", i").write(iName).write(") },").newLine();
            }
            write("{ NULL, 0 }").newLine();
            dedent().write("}").endStmt();
            newLine();
        }

        // ② meta 常量：.base 运行时头 + 扁平方法槽 + 接口虚表
        writeComment("metadata " + sym);
        write("const Feng$Meta_").write(sym).write(" Feng$meta_").write(sym)
                .write(" = {").indent();
        write(".base = {").indent();
        write(".instance_size = sizeof(").write(sym).write("),").newLine();
        vt.parent().use(
                p -> write(".super = (const Feng$Meta*)&Feng$meta_")
                        .write(ClassMeta.symbolName(p.def().symbol()))
                        .write(',').newLine(),
                () -> {
                    if (cd.parent().match(x -> x == ClassDefinition.ObjectClass)) {
                        write(".super = &Feng$meta_$Object,").newLine();
                    } else if (cd.parent().match(ClassDefinition::builtin)) {
                        // builtin 父（Exception 等）无 vtable，meta 常量由运行时定义
                        write(".super = (const Feng$Meta*)&Feng$meta_")
                                .write(ClassMeta.symbolName(
                                        cd.parent().must().symbol()))
                                .write(',').newLine();
                    } else {
                        write(".super = NULL,").newLine();
                    }
                });
        if (vt.ifaces().isEmpty()) {
            write(".iface_count = 0,").newLine();
            write(".ifaces = NULL,").newLine();
        } else {
            write(".iface_count = ").write(vt.ifaces().size()).write(',').newLine();
            write(".ifaces = Feng$meta_").write(sym).write("_ifaces,").newLine();
        }
        var destroy = context.table.classMetas.tryGet(cd.symbol())
                .map(ClassMeta::destroy);
        if (destroy.has()) {
            write(".destroy = (void (*)(void*))")
                    .write(destroy.get().symbol()).write(',').newLine();
        } else {
            write(".destroy = NULL").newLine();
        }
        dedent().write("},").newLine();

        // ③ 方法槽（扁平）：cast 与 TypeWriter 槽字段类型同构
        for (var slot : vt.slots()) {
            write(".").write(slot.name()).write(" = ");
            write(slot.symbol()).write(',').newLine();
        }
        emitIfaceVTables(vt);

        dedent().write("}").endStmt();
        newLine();
    }

    /**
     * 类槽 cast 类型：{@code Ret (*)(<X>*, <params...>)}——与
     * TypeWriter.writeClassMethodSlot 的字段声明共用 {@link TypeWriter#writeClassSlotParams}。
     */
    private void writeSlotCastType(ClassDefinition cd, VTableSlot slot) {
        var pt = slot.signature();
        pt.returnSet().use(t -> context.types.write(t), () -> write("void"));
        write(" (*)(");
        context.types.writeClassSlotParams(cd, pt);
        write(')');
    }

    // ===================================================================
    //  接口虚表（内嵌 i<ifaceKey> 成员）
    // ===================================================================

    /**
     * [S] 本类接口虚表（旧 4119-4155）：{@code .i<ifaceKey> = { .base = {},
     * .<method> = (cast)implSymbol, ... },}。槽名来自 IfaceVTable.slots()（接口
     * allMethods）；符号 = IfaceSlot.symbol()（VTableBuilder 已解析实现类符号）。
     * 槽值必须 cast 成接口方法签名（接口引用是 void*，refer 返回 → void*）。
     */
    private void emitIfaceVTables(VTable vt) {
        for (var ivt : vt.ifaces()) {
            var iName = ivt.metaSymbol().substring("Feng$meta_".length());
            write(".i").write(iName).write(" = {").indent();
            write(".base = {},").newLine();
            for (var slot : ivt.slots()) {
                write(".").write(slot.name()).write(" = (");
                writeIfaceCastType(slot.method().prototype());
                write(')');
                write(slot.symbol());
                write(',').newLine();
            }
            dedent().write("},").newLine();
        }
    }

    /**
     * 接口槽函数指针类型（cast 目标）：与 TypeWriter 槽字段类型一致
     * （refer 返回 → 实际指针类型，如 {@code Int*}，非 {@code void*}）——
     * 否则初始化 incompatible function pointer context.types。
     */
    private void writeIfaceCastType(Prototype pt) {
        pt.returnSet().use(t -> context.types.write(t), () -> write("void"));
        write("(*)(void* self");
        var ps = pt.parameterSet();
        if (!ps.isEmpty()) {
            for (var p : ps) {
                write(", ");
                if (p instanceof FixedParameter fp) {
                    context.types.write(fp.type());
                } else {
                    ErrorUtil.unreachable();
                }
            }
        }
        write(')');
    }
}
