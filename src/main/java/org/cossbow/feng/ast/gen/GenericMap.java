package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.proc.ParameterSet;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.ErrorUtil;

import java.util.Map;
import java.util.function.Function;

/**
 * GenericMap: used for replace type-varieble to specific type.
 */
public class GenericMap {
    /**
     * The GenericMap of master type.
     * <p>
     * Class method can define type-parameters, then the
     * type-parameters of the class should be replaced too.
     */
    private final GenericMap parent;
    private final Map<TypeParameter, TypeDeclarer> map;

    public GenericMap(GenericMap parent,
                      Map<TypeParameter, TypeDeclarer> map) {
        this.parent = parent;
        this.map = Map.copyOf(map);
    }

    public GenericMap(Map<TypeParameter, TypeDeclarer> map) {
        this(null, map);
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
        var n = new GenericMap(parent, map);
        n.next = next;
        return n;
    }

    private TypeDeclarer find(TypeParameter p) {
        var t = map.get(p);
        if (t != null) return t;
        if (parent == null) return null;
        return parent.find(p);
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

    public DerivedTypeDeclarer mapIf(DerivedTypeDeclarer dtd) {
        var dt = dtd.derivedType().clone();
        var nArgs = dt.generic().map(mapper());
        dt.generic(nArgs);
        dt.gm(dt.gm().overlay(this));
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
        var vs = new IdentifierMap<Variable>(p0.parameterSet().size());
        for (var v0 : p0.parameterSet()) {
            var v1 = (Variable) v0.clone();
            v1.type().update(mapper());
            vs.add(v1.name(), v1);
        }
        var ps = new ParameterSet(p0.parameterSet().pos(), vs);
        var rs = p0.returnSet().map(mapper());
        return new Prototype(p0.pos(), ps, rs);
    }

    public boolean isEmpty() {
        return map.isEmpty() &&
                (parent == null || parent.isEmpty());
    }

    public Function<TypeDeclarer, TypeDeclarer> mapper() {
        return this::mapIf;
    }

    //

    public static final GenericMap EMPTY = new GenericMap(Map.of());

    //

    @Override
    public String toString() {
        return map.toString();
    }
}
