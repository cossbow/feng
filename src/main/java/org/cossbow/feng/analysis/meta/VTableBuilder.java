package org.cossbow.feng.analysis.meta;

import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Mangle;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.ast.oop.ClassDefinition;
import org.cossbow.feng.ast.oop.InterfaceDefinition;
import org.cossbow.feng.util.Optional;

import java.util.*;

/**
 * VTable 组装 pass：对每个具体类，从 ClassMeta + 继承/接口图构建运行时虚表布局。
 *
 * <p>只读不改 AST，产出 {@link VTable} 写入 {@code ast.vtables}。
 *
 * <p>类分析已按依赖排序（被依赖者先分析），父类 vtable 必然先于子类存在——子类
 * slots 直接叠加父类 slots：override 原位替换（保父槽位次）、新槽追加。扁平前缀
 * 布局下子类 slots 是父类的严格扩展，派发端 {@code ->m} 直达任意槽。接口虚表槽位
 * 按方法名直接取类 slots（实现完整性已由前置分析保证）。
 */
public final class VTableBuilder {
    private final AnalyseSymbolTable table;
    private final Map<ModulePath, AnalyseSymbolTable> map;

    public VTableBuilder(AnalyseSymbolTable table,
                         Map<ModulePath, AnalyseSymbolTable> map) {
        this.table = table;
        this.map = map;
    }

    private Optional<VTable> find(ClassDefinition def) {
        var sym = def.symbol();
        if (sym.module().none()) return Optional.empty();

        var vt = table.vtables.tryGet(sym);
        if (vt.has()) return vt;

        var tab = map.get(sym.module().must());
        return tab.vtables.tryGet(sym);
    }

    public void build() {
        // 具体化接口索引（mangle symbol → 定义）：mono2 产物。
        // 类 impl() 里的 DerivedType 经 gm.mapIf 后 def() 仍是模板接口
        // （GenericMap.mapIf 只替换 generic、不更新 def），接口 meta 槽位
        // 签名必须来自具体化接口（I_Int 而非 I），否则残留 $O 模板签名。
        var concreteIfaces = new HashMap<String, InterfaceDefinition>();
        for (var list : table.monoAfter.values()) {
            for (var def : list) {
                if (def instanceof InterfaceDefinition id && !id.builtin()) {
                    concreteIfaces.put(ClassMeta.symbolName(id.symbol()), id);
                }
            }
        }
        for (var def : table.monoHead) {
            if (def instanceof InterfaceDefinition id && !id.builtin()) {
                concreteIfaces.put(ClassMeta.symbolName(id.symbol()), id);
            }
        }

        // 父类 vtable 先于子类存在（classMetas 依赖序）：叠加父 slots 后追加自身
        for (var meta : table.classMetas) {
            var cd = meta.def();
            if (cd.builtin() || cd.isFinal()) continue;  // final 类无动态派发，不生成 vtable
            var pvt = cd.parent().get().flatmap(this::find);
            var slots = new IdentifierMap<VTableSlot>();
            if (pvt.has()) slots.addAll(pvt.get().slots()); // 叠加父类 slots
            appendSlots(slots, cd); // 追加当前类 slots（override 原位替换）
            var ifaces = buildIfaceVTables(cd, slots, concreteIfaces);

            table.vtables.add(cd.symbol(), new VTable(cd, slots, ifaces, pvt));
        }
    }

    // ---- slots ----

    /**
     * 追加当前类的 dynamic 方法槽：{@code set} 原位替换（override，保父槽位次）
     * 或追加（新槽）。非 dynamic（运算符/索引宏、mono 方法级实例化）不入虚表。
     */
    private void appendSlots(IdentifierMap<VTableSlot> slots,
                             ClassDefinition cd) {
        var meta = table.classMetas.get(cd.symbol());
        for (var mf : meta.methods()) {
            if (!mf.dynamic()) continue;
            var slot = new VTableSlot(mf);
            slots.set(slot.name(), slot);
        }
    }

    // ---- interface vtables ----

    private List<IfaceVTable> buildIfaceVTables(
            ClassDefinition cd, IdentifierMap<VTableSlot> cSlots,
            Map<String, InterfaceDefinition> concreteIfaces) {
        var ifaces = allConcreteIfaces(cd);
        if (ifaces.isEmpty()) return List.of();

        var result = new ArrayList<IfaceVTable>();
        for (var ifaceDt : ifaces.values()) {
            // 泛型接口的 DerivedType.def() 是模板（mapIf 不更新 def）：
            // 必须取具体化接口定义（符号已 mangle），否则槽位签名残留类型变量。
            var iface = ifaceDt.generic().isEmpty()
                    ? (InterfaceDefinition) ifaceDt.def()
                    : concreteIfaces.get(Mangle.name(ifaceDt));
            if (iface == null) continue;
            var metaSymbol = "Feng$meta_" + Mangle.ifaceKey(ifaceDt);

            // 槽位按方法名直接取类 slots——实现类必定覆盖接口全部方法（前置已检查）
            var slots = new ArrayList<IfaceSlot>();
            for (var im : iface.allMethods().values()) {
                slots.add(new IfaceSlot(im, cSlots.get(im.name())));
            }
            result.add(new IfaceVTable(iface, slots, metaSymbol));
        }
        return result;
    }

    // ---- interface helpers (simplified from CGenerator) ----

    private LinkedHashMap<Identifier, DerivedType>
    allConcreteIfaces(ClassDefinition cd) {
        var result = new LinkedHashMap<Identifier, DerivedType>();
        var cur = cd;
        while (cur != null && cur != ClassDefinition.ObjectClass) {
            for (var dt : cur.impl().values()) {
                var iface = (InterfaceDefinition) dt.def();
                var key = iface.symbol().name();
                if (!result.containsKey(key)) {
                    result.put(key, dt);
                    addIfaceSupers(result, iface);
                }
            }
            if (cur.parent().none()) break;
            cur = cur.parent().must();
        }
        return result;
    }

    private void addIfaceSupers(LinkedHashMap<Identifier, DerivedType> result,
                                InterfaceDefinition iface) {
        for (var superDt : iface.supers()) {
            var superIface = (InterfaceDefinition) superDt.def();
            var key = superIface.symbol().name();
            if (!result.containsKey(key)) {
                result.put(key, superDt);
                addIfaceSupers(result, superIface);
            }
        }
    }
}
