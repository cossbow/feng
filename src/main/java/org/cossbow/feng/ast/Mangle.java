package org.cossbow.feng.ast;

import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Optional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Centralized mangling for concrete monomorphized types and functions.
 * <p>
 * Must stay consistent with {@code CGenerator.mangledName}/{@code typeKey} so
 * that concrete instances get stable, unique C identifiers. The result of
 * {@link #name(DerivedType)} is the full C identifier (module prefix +
 * name + argument suffixes); {@link #symbol(DerivedType)} builds a {@link Symbol}
 * whose {@code toString()} reproduces that identifier via module + name.
 */
public class Mangle {

    protected Mangle() {
    }

    /** 合成模块：运行时 C 函数（Feng$dec 等）与 release 函数的命名空间。 */
    public static final ModulePath FENG =
            new ModulePath(Position.ZERO, new Identifier("Feng"), new Identifier[0]);

    /**
     * Mangled name of a concrete generic instantiation:
     * {@code [module$]Name_Arg1_Arg2}.
     */
    public static String name(DerivedType dt) {
        var sb = new StringBuilder();
        dt.symbol().module().use(m -> sb.append(m).append('$'));
        sb.append(dt.name().value()).append('_');
        sb.append(dt.generic().stream()
                .map(Mangle::typeKey)
                .collect(Collectors.joining("_")));
        return sb.toString();
    }

    /**
     * Symbol (module + mangled suffix identifier) for a concrete generic
     * instantiation. {@code toString()} yields {@link #name(DerivedType)}.
     */
    public static Symbol symbol(DerivedType dt) {
        return symbol(dt.symbol(), dt.generic());
    }

    /**
     * Mangled symbol for a concrete generic function / type instantiation:
     * {@code [module$]Name_Arg1_Arg2}.
     */
    public static Symbol symbol(Symbol base, TypeArguments args) {
        var suffix = base.name().value() + '_' + args.stream()
                .map(Mangle::typeKey).collect(Collectors.joining("_"));
        return new Symbol(base.pos(), base.module(),
                new Identifier(base.pos(), suffix));
    }

    /**
     * Symbol for any concrete mono type declarer (class/interface/array/tuple/
     * named prototype), used as the structural identity key of its definition.
     */
    public static Symbol symbol(TypeDeclarer td) {
        return switch (td) {
            case DerivedTypeDeclarer dtd -> symbol(dtd.derivedType());
            case NamedFuncTypeDeclarer nftd -> symbol(nftd.derivedType());
            // 数组/元组是无名类型，但它们的 typedef 依赖元素类型的完整 struct 定义，
            // 必须发射在元素类型所在模块（definition-site ownership）——否则多模块下
            // 定长数组/元组 typedef 被 defer 到使用模块的结构体定义之后（use-before-typedef）。
            case ArrayTypeDeclarer atd ->
                    new Symbol(atd.pos(), moduleOf(atd.element()).orElse(Optional.of(FENG)),
                            new Identifier(typeKey(atd)));
            case TupleTypeDeclarer ttd ->
                    new Symbol(ttd.pos(), tupleModule(ttd).orElse(Optional.of(FENG)),
                            new Identifier(typeKey(ttd)));
            default -> throw new IllegalArgumentException("not a concrete mono type: " + td);
        };
    }

    /**
     * 元组的归属模块：元素跨多个模块时归确定性合成模块（见
     * {@link #syntheticModule}）；否则沿用 {@link #moduleOf}（单模块或空）。
     */
    private static Optional<ModulePath> tupleModule(TupleTypeDeclarer ttd) {
        var mods = elementModules(ttd);
        if (mods.size() > 1) return Optional.of(syntheticModule(mods));
        return moduleOf(ttd);
    }

    /**
     * 元组所有元素的归属模块集合（去重，保持首次出现序）。
     * 引用元素（{@code *a$A}）与值元素（{@code a$A}）同归属：refer 不影响模块。
     */
    public static Set<ModulePath> elementModules(TupleTypeDeclarer ttd) {
        var set = new LinkedHashSet<ModulePath>();
        for (var e : ttd.elements()) {
            moduleOf(e).use(set::add);
        }
        return set;
    }

    /**
     * 由元素模块集合确定的合成模块路径（确定性命名：按模块 {@code filename}
     * 排序拼接）。同一组元素模块永远映射到同一合成模块。
     */
    public static ModulePath syntheticModule(Collection<ModulePath> elementModules) {
        var names = elementModules.stream()
                .map(ModulePath::filename)
                .distinct()
                .sorted()
                .toArray(String[]::new);
        var values = new Identifier[names.length];
        for (int i = 0; i < names.length; i++) {
            values[i] = new Identifier(names[i]);
        }
        return new ModulePath(Position.ZERO, FENG.pkg(), values);
    }

    /**
     * 数组/元组 mono 的归属模块：递归取元素类型中第一个带 module 的类型定义模块。
     * 元素全为 primitive/内置类型（无 module）时，归属合成模块 {@link #FENG}
     * （其头文件 {@code Feng.h} 被所有模块共享，避免每个模块重复生成
     * {@code [*]int} 等 typedef）。
     */
    private static Optional<ModulePath> moduleOf(TypeDeclarer td) {
        if (td instanceof DerivedTypeDeclarer dtd) {
            return dtd.derivedType().symbol().module();
        }
        if (td instanceof ArrayTypeDeclarer atd) {
            return moduleOf(atd.element());
        }
        if (td instanceof TupleTypeDeclarer ttd) {
            for (var e : ttd.elements()) {
                var m = moduleOf(e);
                if (m.has()) return m;
            }
        }
        if (td instanceof FuncTypeDeclarer ftd) {
            return moduleOf(ftd.prototype());
        }
        return Optional.empty();
    }

    /**
     * 匿名函数类型（AnonFuncTypeDeclarer）的符号：{@code Proto_<protoKey>}。
     * 归属模块按签名（返回值 + 参数）递归取第一个带 module 的类型；全 primitive/
     * 内置时归 {@link #FENG}——不能无脑归 FENG，否则签名引用业务模块类型
     * （如 {@code func(&Node`int,int`) int} → {@code m$Node_Int_Int}）时，
     * typedef 被发射进 Feng.h 却引用不到该类型。
     */
    public static Symbol anonProtoSymbol(Prototype pt) {
        var key = protoKey(pt);
        return new Symbol(pt.pos(), moduleOf(pt).orElse(Optional.of(FENG)),
                new Identifier("Proto_" + key));
    }

    /** 匿名函数原型的归属模块：返回值 + 参数中第一个带 module 的类型。 */
    private static Optional<ModulePath> moduleOf(Prototype pt) {
        var m = pt.returnSet().flatmap(Mangle::moduleOf);
        if (m.has()) return m;
        for (var t : pt.parameterSet().types()) {
            m = moduleOf(t);
            if (m.has()) return m;
        }
        return Optional.empty();
    }

    /**
     * Stable type key, matching {@code CGenerator.typeKey}.
     */
    public static String typeKey(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            var name = capitalize(ptd.primitive().code);
            return ptd.refer().has() ? name + "Ptr" : name;
        }
        if (td instanceof DerivedTypeDeclarer dtd) {
            var dt = dtd.derivedType();
            var n = name(dt);
            return dtd.refer().has() ? n + "Ptr" : n;
        }
        if (td instanceof ArrayTypeDeclarer atd) {
            var r = atd.refer();
            if (r.none()) {
                return "Array_" + typeKey(atd.element()) + "_" + atd.len();
            }
            if (r.get().isKind(ReferKind.PHANTOM)) {
                return "ArrayPRef_" + typeKey(atd.element());
            }
            return "ArraySRef_" + typeKey(atd.element());
        }
        if (td instanceof GenericTypeDeclarer gtd) {
            return gtd.param().name().value();
        }
        if (td instanceof TupleTypeDeclarer ttd) {
            return "Tuple_" + ttd.elements().stream()
                    .map(Mangle::typeKey).collect(Collectors.joining("_"));
        }
        if (td instanceof FuncTypeDeclarer ftd) {
            return protoKey(ftd.prototype());
        }
        if (td instanceof EnumTypeDeclarer) {
            return "Enum";
        }
        return ErrorUtil.unreachable();
    }

    /**
     * Stable key for a prototype based on its resolved signature.
     */
    public static String protoKey(Prototype pt) {
        var sb = new StringBuilder("Proto").append('_');
        if (pt.returnSet().has()) {
            sb.append(typeKey(pt.returnSet().get()));
        } else {
            sb.append("Void");
        }
        for (var t : pt.parameterSet().types()) {
            sb.append('_').append(typeKey(t));
        }
        return sb.toString();
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ---- release 函数命名（destroy / cleanup） ----

    /** 类的 C 符号名：[module$]Name（与 ClassMeta.symbolName / CWriter.write(Symbol) 一致）。 */
    public static String symbolName(Symbol s) {
        var sb = new StringBuilder();
        s.module().use(m -> sb.append(m.toString()));
        sb.append('$').append(s.name().value());
        return sb.toString();
    }

    /** destroy 函数名：Feng$destroy_[module$]Class。 */
    public static String destroyName(Symbol classSymbol) {
        return "Feng$destroy_" + symbolName(classSymbol);
    }

    /** destroy 函数的 Symbol（module=FENG）。 */
    public static Symbol destroySymbol(Symbol classSymbol) {
        return new Symbol(classSymbol.pos(), Optional.of(FENG),
                new Identifier("destroy_" + symbolName(classSymbol)));
    }

    /**
     * 接口 meta 符号键：{@code [module$]Name}（非泛型）或 {@link #name}（泛型具体化）。
     * 接口 meta 常量名 / 类型名 / 内嵌成员名（{@code i<ifaceKey>}）的唯一权威。
     */
    public static String ifaceKey(DerivedType dt) {
        if (dt.generic().isEmpty()) return symbolName(dt.def().symbol());
        return name(dt);
    }

    /** cleanup 函数名：Feng$cleanup_arr_<elem> 或 Feng$cleanup_<typeKey>[_ns]。 */
    public static String cleanupName(TypeDeclarer td) {
        return "Feng$" + cleanupIdentifier(td);
    }

    /** cleanup 函数的 Symbol（module=FENG）。 */
    public static Symbol cleanupSymbol(TypeDeclarer td) {
        return new Symbol(td.pos(), Optional.of(FENG),
                new Identifier(cleanupIdentifier(td)));
    }

    private static String cleanupIdentifier(TypeDeclarer td) {
        // 仅 SRef 数组（refer STRONG）用 cleanup_arr_<ek>；定长数组是值类型，
        // 走 cleanup_<typeKey>（与 SRef 数组区分，避免同名冲突）
        if (td instanceof ArrayTypeDeclarer atd
                && atd.refer().match(r -> r.isKind(ReferKind.STRONG))) {
            return "cleanup_arr_" + typeKey(atd.element());
        }
        return "cleanup_" + typeKey(td) + (td.markSync() ? "" : "_ns");
    }

    /** copy 函数名：Feng$copy_<typeKey>[_ns]（仅值类型含强引用内容，见 CopyBuilder）。 */
    public static String copyName(TypeDeclarer td) {
        return "Feng$copy_" + typeKey(td) + (td.markSync() ? "" : "_ns");
    }

    /** copy 函数的 Symbol（module=FENG）。 */
    public static Symbol copySymbol(TypeDeclarer td) {
        return new Symbol(td.pos(), Optional.of(FENG),
                new Identifier("copy_" + typeKey(td) + (td.markSync() ? "" : "_ns")));
    }
}
