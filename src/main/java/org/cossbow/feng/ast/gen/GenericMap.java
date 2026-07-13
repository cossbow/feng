package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.proc.FixedParameter;
import org.cossbow.feng.ast.proc.Parameter;
import org.cossbow.feng.ast.proc.ParameterSet;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.CommonUtil;
import org.cossbow.feng.util.ErrorUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        if (next == null) return t;
        if (!t.hasTypeVar()) return t;
        return next.mapIf(t);
    }

    public TypeDeclarer mapIf(GenericTypeDeclarer gtd) {
        var t = find(gtd.param());
        if (t == null) return gtd;
        if (next == null) return t;
        if (!t.hasTypeVar()) return t;
        return next.mapIf(t);
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

        return switch (td) {
            case GenericTypeDeclarer gt -> mapIf(gt);
            case DerivedTypeDeclarer dt -> mapIf(dt);
            case ArrayTypeDeclarer atd -> mapIf(atd);
            case TupleTypeDeclarer atd -> mapIf(atd);
            case FuncTypeDeclarer otd -> mapIf(otd);
            default -> td;
        };
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

    public Prototype instantiate(Prototype p0) {
        var vs = new ArrayList<Parameter>(p0.parameterSet().size());
        for (var v0 : p0.parameterSet()) {
            var v = (FixedParameter) v0;
            var t = mapIf(v.type());
            var v1 = new FixedParameter(v0.pos(), v.modifier(), v.name(), t);
            vs.add(v1);
        }
        var ps = new ParameterSet(p0.parameterSet().pos(), vs);
        var rs = p0.returnSet().map(mapper());
        return new Prototype(p0.pos(), ps, rs);
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Function<TypeDeclarer, TypeDeclarer> mapper() {
        return this::mapIf;
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

    public static GenericMap make(
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
