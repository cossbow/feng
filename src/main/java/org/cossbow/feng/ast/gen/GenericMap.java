package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.proc.FixedParameter;
import org.cossbow.feng.ast.proc.Parameter;
import org.cossbow.feng.ast.proc.ParameterSet;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.type.DefinedTypeConstraint;
import org.cossbow.feng.ast.type.TypeConstraint;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.ErrorUtil;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.cossbow.feng.ast.dcl.ReferKind.PHANTOM;
import static org.cossbow.feng.util.ErrorUtil.semantic;

/**
 * GenericMap: used for replace type-varieble to specific type.
 */
public class GenericMap {
    private final Map<TypeParameter, TypeDeclarer> map;

    public GenericMap(Map<TypeParameter, TypeDeclarer> map) {
        this.map = Map.copyOf(map);
    }

    /**
     * GenericMap chain:
     * <p>
     * If use a generic-type {@code T} in other generic-type {@code S},
     * The type variables need to be continuously replaced until all of
     * them are replaced with specific types.
     * <p>
     * The next point points to the next GenericMap that will be used.
     */
    private GenericMap next;

    /**
     * Copy a new GenericMap to make a chain.
     */
    public GenericMap overlay(GenericMap next) {
        if (isEmpty()) return next;
        var n = new GenericMap(map);
        n.next = next;
        return n;
    }

    public GenericMap merge(GenericMap o) {
        if (next != null || o.next != null) {
            return ErrorUtil.unsupported("can't merge with overlay");
        }
        return new GenericMap(CommonUtil.merge(map, o.map));
    }

    private TypeDeclarer find(TypeParameter p) {
        return map.get(p);
    }

    public TypeDeclarer mapIf(TypeParameter tp) {
        var t = find(tp);
        if (t == null)
            return ErrorUtil.unreachable();
        if (next == null || !t.hasTypeVar())
            return (TypeDeclarer) t.clone(); // clone：防止后续修改状态
        return next.mapIf(t);
    }

    public TypeDeclarer mapIf(GenericTypeDeclarer gtd) {
        var t = find(gtd.param());
        if (t == null) return gtd;
        t = (TypeDeclarer) t.clone(); // clone：防止后续修改状态
        if (next == null || !t.hasTypeVar()) {
            // 具体替换：保留使用点的引用形态（*T / &T / 裸 T）。
            // 引用是使用点属性而非类型变量属性：*T 在 T:=Point 后应为 *Point，
            // 否则结果丢失 STRONG/PHANTOM 引用（pre-existing bug）。
            return applyRefer(gtd, t);
        }
        return next.mapIf(t);
    }

    /**
     * 把使用点 {@code gtd} 的引用形态（{@code *} / {@code &} / 无）叠加到替换结果
     * {@code t} 上。替换结果是具体类型时按使用点引用重建；仍是类型参数
     * （收窄克隆）时把引用形态写回克隆参数。
     * <p>
     * 只叠加语法引用（{@code kind()} 有值）：裸 {@code T} 的派生引用（约束投影）
     * 是类型参数自身的属性，不能叠加到具体替换结果——否则接口约束的裸
     * {@code T} 在 {@code T:=Int} 时被改写成 {@code *Int}，破坏值/引用语义。
     */
    private static TypeDeclarer applyRefer(GenericTypeDeclarer gtd, TypeDeclarer t) {
        if (gtd.kind().none()) return t;
        var refer = gtd.maybeRefer();
        if (refer.none()) return t;
        if (t instanceof DerivedTypeDeclarer dtd)
            return new DerivedTypeDeclarer(t.pos(), dtd.derivedType(), refer);
        if (t instanceof PrimitiveTypeDeclarer ptd)
            return ptd.primitive().declarer(t.pos(), refer);
        if (t instanceof GenericTypeDeclarer gtd2)
            return new GenericTypeDeclarer(t.pos(), gtd2.type(),
                    gtd.kind(), gtd.required(), gtd.unmodifiable());
        return t;
    }

    public DerivedType mapIf(DerivedType dt) {
        var ndt = dt.clone();
        var nArgs = ndt.generic().map(mapper());
        ndt.generic(nArgs);
        ndt.gm(ndt.gm().overlay(this));
        return ndt;
    }

    public DerivedTypeDeclarer mapIf(DerivedTypeDeclarer dtd) {
        var dt = mapIf(dtd.derivedType());
        return new DerivedTypeDeclarer(dtd.pos(), dt, dtd.refer());
    }

    public ArrayTypeDeclarer mapIf(ArrayTypeDeclarer atd) {
        var td = (ArrayTypeDeclarer) atd.clone();
        var et = mapIf(atd.element());
        td.element(et);
        return td;
    }

    public TupleTypeDeclarer mapIf(TupleTypeDeclarer td) {
        var ets = td.elements().stream().map(this::mapIf).toList();
        return new TupleTypeDeclarer(td.pos(), ets);
    }

    public FuncTypeDeclarer mapIf(FuncTypeDeclarer ftd) {
        var prot = instantiate(ftd.prototype());
        return new AnonFuncTypeDeclarer(ftd.pos(), ftd.required(), prot);
    }

    public TypeDeclarer mapIf(TypeDeclarer td) {
        if (isEmpty() || !td.hasTypeVar()) return td;

        var nt = switch (td) {
            case GenericTypeDeclarer gt -> mapIf(gt);
            case DerivedTypeDeclarer dt -> mapIf(dt);
            case ArrayTypeDeclarer atd -> mapIf(atd);
            case TupleTypeDeclarer atd -> mapIf(atd);
            case FuncTypeDeclarer otd -> mapIf(otd);
            default -> td;
        };
        nt.markSync(td.markSync());
        return nt;
    }

    public TypeArguments mapAll(TypeArguments tas) {
        if (isEmpty() || tas.isEmpty()) return tas;
        var list = tas.stream().map(mapper()).toList();
        return new TypeArguments(tas.pos(), list);
    }

    public TypeArguments mapAll(TypeParameters tps) {
        if (isEmpty() || tps.isEmpty()) return TypeArguments.EMPTY;
        var list = tps.stream().map(this::mapIf).toList();
        return new TypeArguments(tps.pos(), list);
    }

    public Prototype instantiate(Prototype prot) {
        var li = new ArrayList<Parameter>(prot.parameterSet().size());
        for (var p0 : prot.parameterSet()) {
            var p = (FixedParameter) p0;
            var t = mapIf(p.type());
            var p1 = p.clone();
            p1.type(t);
            p1.var().use(v -> v.type().update(this::mapIf));
            li.add(p1);
        }
        var ps = new ParameterSet(prot.parameterSet().pos(), li);
        var rs = prot.returnSet().map(mapper());
        return new Prototype(prot.pos(), ps, rs, prot.catchAll());
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Function<TypeDeclarer, TypeDeclarer> mapper() {
        return this::mapIf;
    }

    public Set<Map.Entry<TypeParameter, TypeDeclarer>> set() {
        return map.entrySet();
    }

    public void foreach(BiConsumer<TypeParameter, TypeDeclarer> c) {
        map.forEach(c);
    }

    //

    public static final GenericMap EMPTY = new GenericMap(
            Map.of());

    public static GenericMap make(
            Entity e, TypeParameters params, TypeArguments args) {
        return make(e, GenericMap.EMPTY, params, args);
    }

    public static GenericMap make(
            Entity e, boolean checkMiss,
            TypeParameters params, TypeArguments args) {
        return make(e, checkMiss, GenericMap.EMPTY, params, args);
    }

    public static GenericMap make(
            Entity e, GenericMap parent,
            TypeParameters params, TypeArguments args) {
        return make(e, true, parent, params, args);
    }

    public static GenericMap make(
            Entity e, boolean checkMiss,
            GenericMap parent,
            TypeParameters params, TypeArguments args) {
        return make(e, checkMiss, parent,
                params.params().values(), args.arguments());
    }

    public static GenericMap make(
            Entity e,
            List<TypeParameter> params,
            List<TypeDeclarer> args) {
        return make(e, true, GenericMap.EMPTY, params, args);
    }

    private static GenericMap make(
            Entity e, boolean checkMiss,
            GenericMap parent,
            List<TypeParameter> params,
            List<TypeDeclarer> args) {
        if (checkMiss && params.size() > args.size())
            return semantic("mismatch type arguments: %s", e.pos());
        if (params.size() < args.size())
            return semantic("too much type arguments: %s", e.pos());
        if (params.isEmpty()) return parent;

        var gm = new HashMap<TypeParameter, TypeDeclarer>(args.size());
        for (int i = 0; i < args.size(); i++) {
            var p = params.get(i);
            var t = args.get(i);
            if (t.maybeRefer().match(r -> r.isKind(PHANTOM))) {
                return semantic("can't use phantom-refer as type argument: %s",
                        t.pos());
            }
            var nt = parent.mapIf(t);
            var ot = gm.putIfAbsent(p, nt);
            if (ot != null && !nt.equals(ot)) {
                return semantic("'%s' cannot be deduced as both '%s' and '%s' " +
                        "at the same time", p, ot, nt);
            }
        }

        return new GenericMap(gm).merge(parent);
    }

    //

    @Override
    public String toString() {
        return map.toString();
    }
}
