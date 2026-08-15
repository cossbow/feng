package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.FixedParameter;
import org.cossbow.feng.ast.proc.FunctionDefinition;
import org.cossbow.feng.ast.proc.ParameterSet;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.coder.CppGenerator;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Groups;
import org.cossbow.feng.util.Optional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.cossbow.feng.ast.Position.ZERO;

/**
 * AST pass that discovers all concrete generic instantiations (functions,
 * methods, classes, arrays, tuples) and records them in the AnalyseSymbolTable.
 * <p>
 * This pass runs after semantic analysis and before code generation.
 * It does NOT generate any code — it only populates metadata
 * in the symbol table so that backends can consume it.
 * <p>
 * Output data structures:
 * <ul>
 *   <li>{@link AnalyseSymbolTable#concreteTypeInsts} — all concrete type
 *       instantiations in DAG topological order</li>
 *   <li>{@link AnalyseSymbolTable#typeToInst} — mapping from resolved
 *       TypeDeclarer → ConcreteTypeInst</li>
 * </ul>
 */
public class Monomorphization {

    private final AnalyseSymbolTable ast;
    private final Map<ModulePath, AnalyseSymbolTable> importedTables;

    // monomorphization context: current TypeParameters → TypeArguments positional mapping.
    // Set before processing a concrete instantiation's body. null means no generic context.
    private TypeParameters monoParams;
    private TypeArguments monoArgs;

    // Temporary collection for ConcreteTypeInst during discovery, before DAG sorting.
    private final List<ConcreteTypeInst> discoveredTypes = new ArrayList<>();
    // Deduplication: typeKey(td) → ConcreteTypeInst
    private final Map<String, ConcreteTypeInst> discoveredTypeMap = new LinkedHashMap<>();
    // Guard against infinite recursion in registerType when discovering
    // class/interface field & vtable types (e.g. HashMap→Result→HashMap cycle)
    private final Set<DerivedTypeDeclarer> registerTypeSeen = new HashSet<>();

    public Monomorphization(AnalyseSymbolTable ast) {
        this.ast = ast;
        this.importedTables = null;
    }

    public Monomorphization(AnalyseSymbolTable ast,
                            Map<ModulePath, AnalyseSymbolTable> importedTables) {
        this.ast = ast;
        this.importedTables = importedTables;
    }

    // ---- public entry point ----

    public void run() {
        // 1. Scan all function bodies to discover generic instantiations
        for (var fd : ast.functionList) {
            if (fd.builtin()) continue;
            preScanFunc(fd);
        }
        ast.main.use(this::preScanFunc);

        // 2. Discover concrete types inside generic function bodies
        discoverConcreteFuncBodyTypes();

        // 2.5 Discover generic function instantiations from concrete class method bodies.
        // When a generic class method (e.g. HashSet<T>.toList()) calls a generic standalone
        // function (e.g. newVector<T>()), the call is resolved only after the class is
        // instantiated with concrete type args. This step scans class method bodies with
        // the resolved type map to discover those func instantiations.
        discoverClassMethodFuncInsts();

        // 3. Discover generic-parent instantiations of non-generic classes
        for (var cd : ast.dagClasses) {
            if (!cd.generic().isEmpty()) continue;
            if (cd.inherit().has() && cd.inherit().must() instanceof DerivedType idt
                    && !idt.generic().isEmpty()) {
                registerType(new DerivedTypeDeclarer(idt.pos(), idt));
            }
            for (var cm : cd.methods()) {
                preScanMethodClass(cd, cm);
            }
        }

        // 4. Discover types from struct fields and global variables
        for (var sd : ast.dagStructures) {
            for (var sf : sd.fields()) {
                registerType(sf.type());
            }
        }
        for (var gv : ast.constVars) {
            registerType(gv.type().must());
        }
        for (var gv : ast.dagVars) {
            registerType(gv.type().must());
        }

        // 5. Build DAG and topologically sort discovered types
        buildTypeDAG();
    }

    // ---- DAG building and topological sorting ----

    /**
     * After all types are discovered, build a DAG from the dependencies
     * and topologically sort the ConcreteTypeInst list.
     */
    private void buildTypeDAG() {
        if (discoveredTypes.isEmpty() && ast.dagClasses.all().stream()
                .allMatch(cd -> !cd.generic().isEmpty())) {
            ast.concreteTypeInsts = DAGGraph.empty();
            ast.typeToInst = Map.of();
            return;
        }

        // Deduplicate ConcreteTypeInst entries.
        var uniqueTypes = new ArrayList<ConcreteTypeInst>();
        var seenCtis = new HashSet<ConcreteTypeInst>();
        for (var cti : discoveredTypes) {
            if (seenCtis.add(cti)) uniqueTypes.add(cti);
        }

        // Add non-generic classes as identity ConcreteTypeInst entries.
        // Their value-type fields may depend on concrete generic instantiations
        // (e.g. class A { var x Box`int`; } → A depends on Box_Int).
        for (var cd : ast.dagClasses) {
            if (!cd.generic().isEmpty()) continue;
            var key = typeKey(new DerivedTypeDeclarer(cd.symbol().pos(), cd.link()));
            if (discoveredTypeMap.containsKey(key)) continue;
            var cti = new ConcreteTypeInst(cd, Map.of());
            if (seenCtis.add(cti)) uniqueTypes.add(cti);
            discoveredTypeMap.put(key, cti);
        }

        // Build edges: for each ConcreteTypeInst, find dependencies from
        // value-type fields (class embedding) and typeMap values.
        var edges = new ArrayList<Groups.G2<ConcreteTypeInst, ConcreteTypeInst>>();
        for (var cti : uniqueTypes) {
            // Edges from typeMap values (array elements, tuple elements, generic args)
            for (var arg : cti.typeMap().values()) {
                var dep = discoveredTypeMap.get(typeKey(arg));
                if (dep != null && dep != cti) {
                    edges.add(Groups.g2(dep, cti));
                }
            }
            // Edges from value-type fields of non-generic classes
            if (cti.def() instanceof ClassDefinition cd && cd.generic().isEmpty()) {
                for (var cf : cd.fields().values()) {
                    addFieldEdge(cti, cf.type(), edges);
                }
            }
        }

        ast.concreteTypeInsts = DAGGraph.make(uniqueTypes, edges);
        ast.typeToInst = Map.copyOf(discoveredTypeMap);
    }

    /**
     * Add edges from concrete generic instantiations used as value-type
     * fields to the containing class's ConcreteTypeInst.
     */
    private void addFieldEdge(ConcreteTypeInst cti, TypeDeclarer td,
                              List<Groups.G2<ConcreteTypeInst, ConcreteTypeInst>> edges) {
        td = monoResolve(td);
        if (td.hasTypeVar()) return;
        if (td instanceof DerivedTypeDeclarer dtd) {
            if (dtd.refer().none() && !dtd.derivedType().generic().isEmpty()) {
                var dep = discoveredTypeMap.get(typeKey(dtd));
                if (dep != null && dep != cti) {
                    edges.add(Groups.g2(dep, cti));
                }
            }
        } else if (td instanceof ArrayTypeDeclarer atd) {
            if (atd.refer().none()) addFieldEdge(cti, atd.element(), edges);
        }
    }

    // ---- monomorphization context ----

    private void withMono(TypeParameters params, TypeArguments args, Runnable body) {
        var savedParams = monoParams;
        var savedArgs = monoArgs;
        monoParams = params;
        monoArgs = args;
        try {
            body.run();
        } finally {
            monoParams = savedParams;
            monoArgs = savedArgs;
        }
    }

    private void withMonoComposed(TypeParameters classParams, TypeArguments classArgs,
                                  TypeParameters methodParams, TypeArguments methodArgs,
                                  Runnable body) {
        var combinedParams = new IdentifierMap<TypeParameter>();
        if (!classParams.isEmpty()) combinedParams.addAll(classParams.params());
        if (!methodParams.isEmpty()) combinedParams.addAll(methodParams.params());
        var allParams = new TypeParameters(classParams.isEmpty()
                ? methodParams.pos() : classParams.pos(), combinedParams);

        var allArgs = Stream.concat(classArgs.stream(), methodArgs.stream()).toList();
        var combinedArgs = new TypeArguments(classArgs.isEmpty()
                ? methodArgs.pos() : classArgs.pos(), allArgs);

        withMono(allParams, combinedArgs, body);
    }

    // ---- core: type resolution ----

    private static int paramIndex(TypeParameters params, TypeParameter tp) {
        int i = 0;
        for (var p : params) {
            if (p.equals(tp)) return i;
            i++;
        }
        return -1;
    }

    private TypeDeclarer monoResolve(TypeDeclarer td) {
        if (monoParams == null || !td.hasTypeVar()) return td;
        return switch (td) {
            case GenericTypeDeclarer gtd -> {
                int idx = paramIndex(monoParams, gtd.param());
                if (idx < 0) yield td;
                if (idx >= monoArgs.size()) yield td;
                yield monoArgs.get(idx);
            }
            case DerivedTypeDeclarer dtd -> {
                var ndt = dtd.derivedType().clone();
                var newArgs = ndt.generic().stream().map(this::monoResolve).toList();
                ndt.generic(new TypeArguments(ndt.pos(), newArgs));
                yield new DerivedTypeDeclarer(dtd.pos(), ndt, dtd.refer());
            }
            case ArrayTypeDeclarer atd -> {
                var ne = monoResolve(atd.element());
                if (ne == atd.element()) yield atd;
                var na = new ArrayTypeDeclarer(atd.pos(), ne,
                        atd.length(), atd.refer(), atd.literal());
                if (atd.len() != null) na.len(atd.len());
                if (atd.unit() != null) na.unit(atd.unit());
                yield na;
            }
            case TupleTypeDeclarer ttd -> {
                var newElems = ttd.elements().stream()
                        .map(this::monoResolve).toList();
                yield new TupleTypeDeclarer(ttd.pos(), newElems);
            }
            case FuncTypeDeclarer ftd -> {
                // Resolve type variables in the prototype's return and parameter types
                // through the mono context. FuncTypeDeclarer's type vars come from
                // the PrototypeDefinition's generic params.
                var pt = ftd.prototype();
                var resolvedReturn = pt.returnSet().has()
                        ? monoResolve(pt.returnSet().get())
                        : null;
                var resolvedParams = new ArrayList<TypeDeclarer>();
                for (var t : pt.parameterSet().types()) {
                    resolvedParams.add(monoResolve(t));
                }
                boolean allResolved = (resolvedReturn == null || !resolvedReturn.hasTypeVar());
                for (var t : resolvedParams) {
                    if (t.hasTypeVar()) allResolved = false;
                }
                if (!allResolved) yield ftd;
                // Create new Prototype with resolved types so hasTypeVar() returns false
                var newPt = new Prototype(pt.pos(),
                        ParameterSet.anon(resolvedParams),
                        pt.returnSet().has() ? Optional.of(resolvedReturn) : pt.returnSet());
                if (ftd instanceof NamedFuncTypeDeclarer nftd) {
                    var result = new NamedFuncTypeDeclarer(nftd.pos(), nftd.required(), nftd.derivedType(), nftd.def());
                    result.prototype(newPt);
                    yield result;
                } else {
                    yield new AnonFuncTypeDeclarer(ftd.pos(), ftd.required(), newPt);
                }
            }
            default -> td;
        };
    }

    /**
     * Resolve type variables using a Map<TypeParameter, TypeDeclarer>.
     * Used for PrototypeDefinition instantiations where type variables are
     * resolved through the ConcreteTypeInst's typeMap.
     */
    private TypeDeclarer resolveFromMap(TypeDeclarer td, Map<TypeParameter, TypeDeclarer> typeMap) {
        if (!td.hasTypeVar() || typeMap == null || typeMap.isEmpty()) return td;
        return switch (td) {
            case GenericTypeDeclarer gtd -> {
                var tp = gtd.param();
                var resolved = typeMap.get(tp);
                if (resolved != null) yield resolved;
                String name = tp.name().value();
                for (var e : typeMap.entrySet()) {
                    if (e.getKey().name().value().equals(name)) yield e.getValue();
                }
                yield td;
            }
            case DerivedTypeDeclarer dtd -> {
                var ndt = dtd.derivedType().clone();
                var newArgs = ndt.generic().stream()
                        .map(a -> resolveFromMap(a, typeMap)).toList();
                ndt.generic(new TypeArguments(ndt.pos(), newArgs));
                yield new DerivedTypeDeclarer(dtd.pos(), ndt, dtd.refer());
            }
            case ArrayTypeDeclarer atd -> {
                var elType = resolveFromMap(atd.element(), typeMap);
                if (elType == atd.element()) yield atd;
                var na = new ArrayTypeDeclarer(atd.pos(), elType,
                        atd.length(), atd.refer(), atd.literal());
                if (atd.len() != null) na.len(atd.len());
                if (atd.unit() != null) na.unit(atd.unit());
                yield na;
            }
            case TupleTypeDeclarer ttd -> {
                var newElems = ttd.elements().stream()
                        .map(e -> resolveFromMap(e, typeMap)).toList();
                yield new TupleTypeDeclarer(ttd.pos(), newElems);
            }
            default -> td;
        };
    }

    private TypeDeclarer resolveByPosition(TypeParameters params, TypeArguments args, TypeDeclarer td) {
        if (!td.hasTypeVar() || args == null) return td;
        return switch (td) {
            case GenericTypeDeclarer gtd -> {
                int idx = paramIndex(params, gtd.param());
                if (idx < 0) {
                    String name = gtd.param().name().value();
                    int i = 0;
                    for (var p : params) {
                        if (p.name().value().equals(name)) {
                            idx = i;
                            break;
                        }
                        i++;
                    }
                }
                if (idx < 0 || idx >= args.size()) yield td;
                yield args.get(idx);
            }
            case DerivedTypeDeclarer dtd -> {
                var ndt = dtd.derivedType().clone();
                var newArgs = ndt.generic().stream()
                        .map(a -> resolveByPosition(params, args, a)).toList();
                ndt.generic(new TypeArguments(ndt.pos(), newArgs));
                yield new DerivedTypeDeclarer(dtd.pos(), ndt, dtd.refer());
            }
            case ArrayTypeDeclarer atd -> {
                var elType = resolveByPosition(params, args, atd.element());
                yield new ArrayTypeDeclarer(atd.pos(), elType,
                        atd.length(), atd.refer(), atd.literal());
            }
            case TupleTypeDeclarer ttd -> {
                var newElems = ttd.elements().stream()
                        .map(e -> resolveByPosition(params, args, e)).toList();
                yield new TupleTypeDeclarer(ttd.pos(), newElems);
            }
            default -> td;
        };
    }

    // ---- register type: records concrete instantiations ----

    /**
     * Register a type: resolves type variables, creates ConcreteTypeInst for
     * array/tuple/derived types, and records them for later DAG sorting.
     * <p>
     * Also populates the deprecated concreteInstantiations for backward
     * compatibility with CGenerator until it is refactored.
     */
    private void registerType(TypeDeclarer td) {
        td = monoResolve(td);
        if (td.hasTypeVar()) return;

        switch (td) {
            case GenericTypeDeclarer gtd -> {
                // Semantic analysis should have resolved all type variables.
                // If we still see a GenericTypeDeclarer after monoResolve,
                // it means a type variable could not be resolved — this is an error.
                ErrorUtil.unreachable();
            }
            case DerivedTypeDeclarer dtd -> {
                // Guard against infinite recursion (e.g., HashMap→Result→HashMap cycle)
                if (!registerTypeSeen.add(dtd)) return;
                var dt = dtd.derivedType();
                // Recurse into generic args first (they may produce new ConcreteTypeInst)
                for (var arg : dt.generic()) {
                    registerType(arg);
                }
                if (!dt.generic().isEmpty() && !dt.hasTypeVar()) {
                    var def = dt.def();
                    if (def instanceof ClassDefinition cd) {
                        withMono(cd.generic(), dt.generic(), () -> {
                            if (cd.inherit().has() && cd.inherit().must() instanceof DerivedType idt) {
                                var resolvedArgs = idt.generic().stream()
                                        .map(this::monoResolve).toList();
                                var resolvedDt = new DerivedType(idt.pos(), idt.symbol(),
                                        new TypeArguments(idt.pos(), resolvedArgs));
                                resolvedDt.def(idt.def());
                                registerType(new DerivedTypeDeclarer(idt.pos(), resolvedDt));
                            }
                        });
                        withMono(cd.generic(), dt.generic(), () -> {
                            for (var ifaceDt : allConcreteIfaces(cd).values()) {
                                if (!ifaceDt.generic().isEmpty() && !ifaceDt.hasTypeVar()) {
                                    registerType(new DerivedTypeDeclarer(ifaceDt.pos(), ifaceDt));
                                }
                            }
                        });
                        // Register class field types for typedef emission
                        withMono(cd.generic(), dt.generic(), () -> {
                            for (var f : cd.allFields().values()) {
                                registerType(f.type());
                            }
                            for (var cm : cd.methods()) {
                                if (!cm.generic().isEmpty()) continue;
                                var pt = cm.prototype();
                                pt.returnSet().use(this::registerType);
                                for (var t : pt.parameterSet().types()) registerType(t);
                            }
                        });
                    }
                    if (def instanceof InterfaceDefinition id) {
                        // Register interface method signature types for typedef emission
                        withMono(id.generic(), dt.generic(), () -> {
                            for (var im : id.allMethods()) {
                                var pt = im.prototype();
                                pt.returnSet().use(this::registerType);
                                for (var t : pt.parameterSet().types()) registerType(t);
                            }
                        });
                    }
                    // Backward compatibility: populate deprecated field
                    ast.concreteInstantiations.add(dt);
                }
            }
            case ArrayTypeDeclarer atd -> {
                // Recurse into element type first
                registerType(atd.element());
                // Dedup: only add if not already present
                if (!discoveredTypeMap.containsKey(typeKey(atd))) {
                    var def = buildArrayDefinition(atd);
                    var typeMap = buildTypeMapForArray(def, atd);
                    var cti = new ConcreteTypeInst(def, typeMap);
                    discoveredTypes.add(cti);
                    discoveredTypeMap.put(typeKey(atd), cti);
                }
            }
            case TupleTypeDeclarer ttd -> {
                // Recurse into element types first
                for (var et : ttd.elements()) {
                    registerType(et);
                }
                // Dedup: only add if not already present
                if (!discoveredTypeMap.containsKey(typeKey(ttd))) {
                    var def = buildTupleDefinition(ttd);
                    var typeMap = buildTypeMapForTuple(def, ttd);
                    var cti = new ConcreteTypeInst(def, typeMap);
                    discoveredTypes.add(cti);
                    discoveredTypeMap.put(typeKey(ttd), cti);
                }
            }
            case FuncTypeDeclarer ftd -> {
                // NamedFuncTypeDeclarer with resolved generic prototype params
                // → create ConcreteTypeInst for the PrototypeDefinition
                if (ftd instanceof NamedFuncTypeDeclarer nftd) {
                    var typeMap = buildTypeMapForPrototype(nftd);
                    // Compute key from the prototype's resolved signature
                    var key = protoKeyForMono(nftd.prototype(), typeMap);
                    if (!discoveredTypeMap.containsKey(key)) {
                        var cti = new ConcreteTypeInst(nftd.def().get().must(), typeMap);
                        discoveredTypes.add(cti);
                        discoveredTypeMap.put(key, cti);
                        // Backward compatibility
                        ast.concreteInstantiations.add(nftd.derivedType());
                    }
                }
                // AnonFuncTypeDeclarer: no TypeDefinition, handled by CGenerator directly
            }
            default -> {}
        }
    }

    // ---- build TypeDefinition for array/tuple ----

    /**
     * Create the appropriate BuiltinTypeDefinition for an ArrayTypeDeclarer.
     * - Reference array ([&]T or [&?]T): ArrayRefDefinition (takes precedence)
     * - Fixed array ([N]T): FixedArrayDefinition
     */
    private org.cossbow.feng.ast.TypeDefinition buildArrayDefinition(ArrayTypeDeclarer atd) {
        var elementParam = new TypeParameter(ZERO, new Identifier("E"),
                Optional.empty());
        if (atd.refer().has()) {
            // Reference array: [&]T (SRef) or [&?]T (PRef) — takes precedence
            // over FixedArray, because e.g. [&][3][2]int is a reference
            // to a 2D array, not a fixed array of 3D elements.
            boolean phantom = atd.refer().get().kind() == ReferKind.PHANTOM;
            return new ArrayRefDefinition(elementParam, phantom);
        } else {
            // Fixed array: [N]T
            return new FixedArrayDefinition(elementParam, atd.len().intValue());
        }
    }

    /**
     * Create a TupleDefinition from a TupleTypeDeclarer.
     */
    private TupleDefinition buildTupleDefinition(TupleTypeDeclarer ttd) {
        var elementParams = new ArrayList<TypeParameter>();
        int i = 0;
        for (var ignored : ttd.elements()) {
            elementParams.add(new TypeParameter(ZERO, new Identifier("T" + i),
                    Optional.empty()));
            i++;
        }
        return new TupleDefinition(elementParams);
    }

    // ---- build typeMap for ConcreteTypeInst ----

    /**
     * Build TypeParameter → TypeDeclarer mapping for a DerivedTypeDeclarer.
     * Maps the class/interface generic params to their concrete type arguments
     * from the current monoParams/monoArgs context.
     */
    private Map<TypeParameter, TypeDeclarer> buildTypeMapForDerived(DerivedTypeDeclarer dtd) {
        var map = new LinkedHashMap<TypeParameter, TypeDeclarer>();
        var dtGeneric = dtd.derivedType().generic();
        var def = dtd.def();
        // Map definition TypeParameters → concrete TypeDeclarers from the
        // DerivedType's generic args. Works both with and without mono context
        // because the generic args are already resolved by monoResolve.
        for (int i = 0; i < dtGeneric.size() && i < def.generic().size(); i++) {
            map.put(def.generic().get(i), dtGeneric.get(i));
        }
        return map;
    }

    /**
     * Build TypeParameter → TypeDeclarer mapping for an array type.
     * Maps the BuiltinTypeDefinition's elementParam to the resolved element type.
     */
    private Map<TypeParameter, TypeDeclarer> buildTypeMapForArray(
            org.cossbow.feng.ast.TypeDefinition def, ArrayTypeDeclarer atd) {
        var map = new LinkedHashMap<TypeParameter, TypeDeclarer>();
        switch (def) {
            case FixedArrayDefinition fad -> map.put(fad.elementParam(), atd.element());
            case ArrayRefDefinition ard -> map.put(ard.elementParam(), atd.element());
            default -> {}
        }
        return map;
    }

    /**
     * Build TypeParameter → TypeDeclarer mapping for a tuple type.
     * Maps each elementParam to the corresponding resolved element type.
     */
    private Map<TypeParameter, TypeDeclarer> buildTypeMapForTuple(
            TupleDefinition def, TupleTypeDeclarer ttd) {
        var map = new LinkedHashMap<TypeParameter, TypeDeclarer>();
        var params = def.elementParams();
        var elems = ttd.elements();
        for (int i = 0; i < params.size() && i < elems.size(); i++) {
            map.put(params.get(i), elems.get(i));
        }
        return map;
    }

    /**
     * Build TypeParameter → TypeDeclarer mapping for a generic prototype.
     * Maps the PrototypeDefinition's generic params to their concrete type arguments
     * from the NamedFuncTypeDeclarer's derivedType().generic().
     */
    private Map<TypeParameter, TypeDeclarer> buildTypeMapForPrototype(NamedFuncTypeDeclarer nftd) {
        var map = new LinkedHashMap<TypeParameter, TypeDeclarer>();
        var dtGeneric = nftd.derivedType().generic();
        var defGeneric = nftd.def().get().must().generic();
        for (int i = 0; i < dtGeneric.size() && i < defGeneric.size(); i++) {
            map.put(defGeneric.get(i), dtGeneric.get(i));
        }
        return map;
    }

    /**
     * Collect all interfaces (direct + inherited) with concrete type arguments resolved.
     * Must be called within {@code withMono(cd.generic(), dt.generic(), ...)}.
     */
    private LinkedHashMap<Identifier, DerivedType> allConcreteIfaces(ClassDefinition cd) {
        var result = new LinkedHashMap<Identifier, DerivedType>();

        // Walk up ancestor chain, child first (so child's impl takes priority)
        var cur = cd;
        while (cur != null && cur != ClassDefinition.ObjectClass) {
            var ancCur = cur; // capture for lambda
            ancCur.impl().each((sym, ifaceDt) -> {
                var key = sym.name();
                // Already implemented by a child class
                if (result.containsKey(key)) return;
                var resolved = resolveAncestorIface(cd, ancCur, ifaceDt);
                if (resolved != null) result.put(key, resolved);
            });
            if (cur.parent().none()) break;
            cur = cur.parent().must();
        }
        return result;
    }

    /**
     * Resolve interface type args from an ancestor's param space to the current class's
     * concrete type args. Must be called within {@code withMono(cd.generic(), dt.generic(), ...)}.
     */
    private DerivedType resolveAncestorIface(ClassDefinition cd, ClassDefinition anc,
                                             DerivedType ancIface) {
        var clone = ancIface.clone();
        var newArgs = ancIface.generic().stream()
                .map(arg -> resolveArgFromAncestor(cd, anc, arg))
                .toList();
        clone.generic(new TypeArguments(clone.pos(), newArgs));
        return clone;
    }

    /**
     * Resolve a single type arg from an ancestor's param space through the inheritance chain
     * to concrete. Must be called within {@code withMono(cd.generic(), dt.generic(), ...)}.
     */
    private TypeDeclarer resolveArgFromAncestor(ClassDefinition cd, ClassDefinition anc,
                                                TypeDeclarer arg) {
        if (!arg.hasTypeVar()) return arg;
        if (anc == cd) return resolveByPosition(cd.generic(), monoArgs, arg);

        // Walk from anc down to cd, applying positional type parameter substitution at each level
        var cur = anc;
        TypeDeclarer result = arg;
        while (cur != cd) {
            var child = findChild(cur, cd);
            if (child == null) break;
            var inheritArgs = ((DerivedType) child.inherit().must()).generic();
            result = resolveByPosition(cur.generic(), inheritArgs, result);
            cur = child;
        }

        // Final: resolve cd.params → concrete using positional mapping
        return resolveByPosition(cd.generic(), monoArgs, result);
    }

    /**
     * Find the immediate child of parent that is an ancestor of descendant.
     */
    private ClassDefinition findChild(ClassDefinition parent, ClassDefinition descendant) {
        var cur = descendant;
        while (cur.parent().has() && cur.parent().must() != ClassDefinition.ObjectClass) {
            if (cur.parent().must() == parent) return cur;
            cur = cur.parent().must();
        }
        return null;
    }

    // ---- pre-scan: discover generic instantiations ----

    private void preScanFunc(FunctionDefinition fd) {
        for (var p : fd.prototype().parameterSet()) {
            if (p instanceof FixedParameter fp) {
                registerType(fp.type());
            }
        }
        fd.prototype().returnSet().use(this::registerType);
        fd.procedure().use(proc -> preScanStmt(proc.body()));
    }

    private void preScanMethodClass(ClassDefinition cd, ClassMethod cm) {
        if (!cm.generic().isEmpty() && !typeParamsHasTypeVar(cm.generic())) {
            var classDt = new DerivedType(cm.pos(), cd.symbol(), TypeArguments.EMPTY);
            classDt.def(cd);
            ast.concreteMethodInsts.add(
                    new MethodInstantiation(classDt, cm, TypeArguments.EMPTY));
        }
        for (var p : cm.prototype().parameterSet()) {
            if (p instanceof FixedParameter fp) {
                registerType(fp.type());
            }
        }
        // Scan method body for generic instantiations
        cm.procedure().use(proc -> preScanStmt(proc.body()));
    }

    private static boolean typeParamsHasTypeVar(TypeParameters params) {
        for (var tp : params) {
            // TypeParameter itself is a type variable by definition
            // If used as GenericTypeDeclarer it would haveTypeVar, but
            // TypeParameters with any params means they are type variables.
            return true;
        }
        return false;
    }

    private void preScanStmt(Statement stmt) {
        if (stmt instanceof DeclarationStatement ds) {
            for (var v : ds.variables()) {
                registerType(v.type().must());
                v.value().use(this::preScanExpr);
            }
        } else if (stmt instanceof BlockStatement bs) {
            for (var s : bs.list()) preScanStmt(s);
        } else if (stmt instanceof IfStatement is) {
            is.init().use(this::preScanStmt);
            preScanExpr(is.condition());
            preScanStmt(is.yes());
            is.not().use(this::preScanStmt);
        } else if (stmt instanceof ForStatement fs) {
            if (fs instanceof ConditionalForStatement cfs) {
                cfs.initializer().use(this::preScanStmt);
                preScanStmt(cfs.body());
            }
        } else if (stmt instanceof SwitchStatement ss) {
            for (var br : ss.branches()) preScanStmt(br);
        } else if (stmt instanceof CallStatement cs) {
            preScanExpr(cs.call());
            // The call may have been replaced (e.g. format builtin expansion, RelayLowering
            // temp-variable insertion). Scan the replacement so types discovered there
            // (e.g. [32]byte arrays from formatPrimitive) are registered.
            cs.replace().use(this::preScanStmt);
        } else if (stmt instanceof ReturnStatement rs) {
            rs.result().use(this::preScanExpr);
        } else if (stmt instanceof AssignmentsStatement as) {
            for (int i = 0; i < as.list().size(); i++) {
                preScanExpr(as.value(i));
            }
        }
    }

    private void preScanExpr(Expression e) {
        if (e instanceof CallExpression ce) {
            if (ce.callee() instanceof SymbolExpression se
                    && !se.generic().isEmpty()) {
                registerFuncInstantiation(se.symbol(), se.generic());
            }
            if (ce.callee() instanceof FunctionExpression fe
                    && !fe.generic().isEmpty()) {
                registerFuncInstantiation(fe.symbol(), fe.generic());
            }
            if (ce.callee() instanceof MethodExpression me
                    && !me.generic().isEmpty()) {
                var resolved = me.generic().stream().map(this::monoResolve).toList();
                if (!resolved.stream().allMatch(TypeDeclarer::hasTypeVar)) {
                    var resolvedArgs = new TypeArguments(me.generic().pos(), resolved);
                    me.subject().resultType.use(td -> {
                        if (td instanceof DerivedTypeDeclarer dtd
                                && dtd.def() instanceof ClassDefinition cd) {
                            registerMethodInst(cd, dtd.derivedType(), me, resolvedArgs);
                        }
                    }, () -> {
                    });
                }
            }
            preScanExpr(ce.callee());
            for (var a : ce.arguments()) preScanExpr(a);
        } else if (e instanceof NewExpression ne) {
            ne.resultType.use(this::registerType);
            ne.arg().use(this::preScanExpr);
        } else if (e instanceof MethodExpression me) {
            if (me.subject() instanceof Expression se) preScanExpr(se);
            if (!me.generic().isEmpty()) {
                var resolved = me.generic().stream().map(this::monoResolve).toList();
                if (!resolved.stream().allMatch(TypeDeclarer::hasTypeVar)) {
                    var resolvedArgs = new TypeArguments(me.generic().pos(), resolved);
                    me.subject().resultType.use(td -> {
                        if (td instanceof DerivedTypeDeclarer dtd
                                && dtd.def() instanceof ClassDefinition cd) {
                            registerMethodInst(cd, dtd.derivedType(), me, resolvedArgs);
                        }
                    }, () -> {
                    });
                }
            }
        } else if (e instanceof SymbolExpression se && !se.generic().isEmpty()) {
            registerFuncInstantiation(se.symbol(), se.generic());
        } else if (e instanceof FunctionExpression fe && !fe.generic().isEmpty()) {
            registerFuncInstantiation(fe.symbol(), fe.generic());
        } else if (e instanceof ObjectExpression oe) {
            for (var val : oe.entries().values()) preScanExpr(val);
        } else if (e instanceof MemberOfExpression moe) {
            preScanExpr(moe.subject());
        } else if (e instanceof ArrayExpression ae) {
            ae.resultType.use(this::registerType);
            for (var elem : ae.elements()) preScanExpr(elem);
        } else if (e instanceof BinaryExpression be) {
            preScanExpr(be.left());
            preScanExpr(be.right());
        } else if (e instanceof UnaryExpression ue) {
            preScanExpr(ue.operand());
        } else if (e instanceof BlockExpression be) {
            // RelayLowering wraps temporaries (incl. generic calls) into block
            // expressions; recurse so their concrete instantiations are found.
            for (var s : be.block()) preScanStmt(s);
            preScanExpr(be.result());
        }
    }

    /**
     * Register a FuncInstantiation for a generic function call.
     * Handles both same-module and cross-module cases.
     */
    private void registerFuncInstantiation(Symbol sym, TypeArguments genericArgs) {
        var resolved = genericArgs.stream().map(this::monoResolve).toList();
        if (resolved.stream().allMatch(TypeDeclarer::hasTypeVar)) return;
        var resolvedArgs = new TypeArguments(genericArgs.pos(), resolved);
        var fd = findFuncDef(sym);
        if (fd != null) {
            ast.concreteFuncInsts.add(new FuncInstantiation(fd, resolvedArgs));
            withMono(fd.generic(), resolvedArgs, () -> {
                fd.prototype().returnSet().use(this::registerType);
                for (var t : fd.prototype().parameterSet().types()) {
                    registerType(t);
                }
            });
        }
    }

    // ---- helpers ----

    private void registerMethodInst(ClassDefinition cd, DerivedType classDt,
                                    MethodExpression me, TypeArguments resolvedGeneric) {
        var owner = cd;
        while (!owner.methods().exists(me.method().name()) && owner.parent().has()
                && owner.parent().must() != ClassDefinition.ObjectClass) {
            owner = owner.parent().must();
        }
        var cm = owner.methods().tryGet(me.method().name());
        if (!cm.has()) return;
        var ownerDt = owner == cd ? classDt : ancestorDt(cd, classDt, owner);
        ast.concreteMethodInsts.add(new MethodInstantiation(ownerDt, cm.get(), resolvedGeneric));
        withMonoComposed(owner.generic(), ownerDt.generic(),
                cm.get().generic(), resolvedGeneric, () -> {
                    var pt = cm.get().prototype();
                    pt.returnSet().use(this::registerType);
                    for (var t : pt.parameterSet().types()) registerType(t);
                });
    }

    /**
     * Compute the concrete DerivedType for an ancestor class of a generic instantiation.
     * Resolves anc's type params through the inheritance chain to concrete args.
     * Must be called within {@code withMono(cd.generic(), dt.generic(), ...)}.
     */
    private DerivedType ancestorDt(ClassDefinition cd, DerivedType dt, ClassDefinition anc) {
        if (anc == cd) return dt;
        if (anc.generic().isEmpty()) {
            var result = new DerivedType(anc.symbol().pos(), anc.symbol(), TypeArguments.EMPTY);
            result.def(anc);
            return result;
        }
        // Resolve anc's type params through the chain to concrete args.
        var ancArgs = anc.generic().stream()
                .map(tp -> {
                    var gtd = new GenericTypeDeclarer(tp.pos(), new GenericType(tp.pos(), tp));
                    return resolveArgFromAncestor(cd, anc, gtd);
                }).toList();
        var typeArgs = new TypeArguments(anc.generic().pos(), ancArgs);
        var result = new DerivedType(anc.symbol().pos(), anc.symbol(), typeArgs);
        result.def(anc);
        return result;
    }

    private FunctionDefinition findFuncDef(Symbol symbol) {
        for (var fd : ast.functionList) {
            if (fd.symbol().equals(symbol)) return fd;
        }
        if (importedTables != null && symbol.module().has()) {
            var imported = importedTables.get(symbol.module().get());
            if (imported != null) {
                for (var fd : imported.functionList) {
                    if (fd.symbol().equals(symbol)) return fd;
                }
            }
        }
        return null;
    }

    /**
     * Find the AnalyseSymbolTable of the module that defines the given symbol.
     * This is the current module's AST if the function is local, or the
     * imported module's AST for cross-module functions.
     */
    private AnalyseSymbolTable findDefModule(Symbol symbol) {
        if (symbol.module().has() && importedTables != null) {
            return importedTables.get(symbol.module().get());
        }
        return null;
    }

    private String typeKey(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            var name = CppGenerator.PrimitiveName.get(ptd.primitive());
            return ptd.refer().has() ? name + "Ptr" : name;
        }
        if (td instanceof DerivedTypeDeclarer dtd) {
            var sb = new StringBuilder();
            dtd.def().symbol().module().use(sb::append);
            sb.append('_').append(dtd.def().symbol().name().value());
            if (!dtd.generic().isEmpty()) {
                for (var a : dtd.generic()) {
                    sb.append('_').append(typeKey(a));
                }
            }
            if (dtd.refer().has()) sb.append("Ptr");
            return sb.toString();
        }
        if (td instanceof ArrayTypeDeclarer atd) {
            var r = atd.refer();
            if (r.none()) return "Array_" + typeKey(atd.element())
                    + "_" + atd.len();
            if (r.get().isKind(ReferKind.PHANTOM)) return "ArrayPRef_" + typeKey(atd.element());
            return "ArraySRef_" + typeKey(atd.element());
        }
        if (td instanceof TupleTypeDeclarer ttd) {
            return "Tuple_" + ttd.elements().stream()
                    .map(this::typeKey).collect(Collectors.joining("_"));
        }
        return "Unknown";
    }

    /**
     * Compute the proto key for a PrototypeDefinition instantiation.
     * Must produce the same key as CGenerator.protoKey() for consistency.
     */
    private String protoKeyForMono(Prototype pt, Map<TypeParameter, TypeDeclarer> typeMap) {
        var sb = new StringBuilder("Proto");
        sb.append('_');
        if (pt.returnSet().has()) {
            sb.append(typeKeyResolved(pt.returnSet().get(), typeMap));
        } else {
            sb.append("Void");
        }
        for (var t : pt.parameterSet().types()) {
            sb.append('_').append(typeKeyResolved(t, typeMap));
        }
        return sb.toString();
    }

    /**
     * typeKey with explicit typeMap resolution (for PrototypeDefinition instantiations
     * where the type variables are resolved through the ConcreteTypeInst's typeMap
     * rather than through the ambient monoParams/monoArgs).
     */
    private String typeKeyResolved(TypeDeclarer td, Map<TypeParameter, TypeDeclarer> typeMap) {
        if (typeMap != null && td.hasTypeVar()) {
            td = resolveFromMap(td, typeMap);
        }
        return typeKey(td);
    }

    // ---- discover concrete types inside generic function bodies ----

    private void discoverConcreteFuncBodyTypes() {
        // Collect all concrete function instantiations, including those
        // that were registered in imported module tables (cross-module calls).
        // registerType() adds to discoveredTypes (current module),
        // so scanning cross-module func bodies here will discover types like
        // HashMap_Int_Int that are needed by the current module.
        var allFi = new LinkedHashSet<>(ast.concreteFuncInsts);
        if (importedTables != null) {
            for (var impTable : importedTables.values()) {
                allFi.addAll(impTable.concreteFuncInsts);
            }
        }
        if (allFi.isEmpty()) return;

        var initial = new LinkedHashSet<>(ast.concreteFuncInsts);
        ast.concreteFuncInsts.clear();

        var processed = new HashSet<FuncInstantiation>();
        var allDiscovered = new LinkedHashSet<FuncInstantiation>();
        var worklist = new ArrayList<>(allFi);
        int i = 0;
        while (i < worklist.size()) {
            var fi = worklist.get(i++);
            if (fi.args().hasTypeVar()) continue;
            if (!processed.add(fi)) continue;
            withMono(fi.fd().generic(), fi.args(), () -> preScanFunc(fi.fd()));
            allDiscovered.addAll(ast.concreteFuncInsts);
            worklist.addAll(ast.concreteFuncInsts);
            ast.concreteFuncInsts.clear();
        }

        ast.concreteFuncInsts.addAll(initial);
        ast.concreteFuncInsts.addAll(allDiscovered);
    }

    /**
     * Discover generic function instantiations from concrete class method bodies.
     * When a generic class method (e.g. {@code HashSet<T>.toList()}) calls a generic
     * standalone function (e.g. {@code newVector<T>()}), the call site is discovered
     * only when the method body is scanned with the resolved type map (T → Int).
     * <p>
     * Also handles transitive discovery: if a newly discovered func instantiation's
     * body calls further generic functions, those are discovered too.
     */
    private void discoverClassMethodFuncInsts() {
        var allDiscoveredFromClasses = new LinkedHashSet<FuncInstantiation>();

        for (var dt : ast.concreteInstantiations) {
            if (!(dt.def() instanceof ClassDefinition cd) || cd.generic().isEmpty()) continue;
            if (dt.generic().isEmpty() || dt.hasTypeVar()) continue;
            var typeParams = cd.generic();
            var typeArgs = dt.generic();

            withMono(typeParams, typeArgs, () -> {
                for (var cm : cd.methods()) {
                    if (!cm.generic().isEmpty()) continue;
                    cm.procedure().use(proc -> preScanStmt(proc.body()));
                }
            });
            allDiscoveredFromClasses.addAll(ast.concreteFuncInsts);
            ast.concreteFuncInsts.clear();
        }

        if (allDiscoveredFromClasses.isEmpty()) return;

        // BFS: transitively discover func instantiations from bodies of
        // newly discovered func instantiations
        var processed = new HashSet<FuncInstantiation>();
        var worklist = new ArrayList<>(allDiscoveredFromClasses);
        int i = 0;
        while (i < worklist.size()) {
            var fi = worklist.get(i++);
            if (fi.args().hasTypeVar()) continue;
            if (!processed.add(fi)) continue;
            withMono(fi.fd().generic(), fi.args(), () -> preScanFunc(fi.fd()));
            worklist.addAll(ast.concreteFuncInsts);
            ast.concreteFuncInsts.clear();
        }

        ast.concreteFuncInsts.addAll(allDiscoveredFromClasses);
        ast.concreteFuncInsts.addAll(processed);
    }
}
