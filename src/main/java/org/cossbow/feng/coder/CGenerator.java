package org.cossbow.feng.coder;

import org.cossbow.feng.analysis.AnalyseSymbolTable;
import org.cossbow.feng.analysis.ConcreteTypeInst;
import org.cossbow.feng.analysis.FuncInstantiation;
import org.cossbow.feng.analysis.MethodInstantiation;
import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.*;
import org.cossbow.feng.ast.gen.*;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.lit.Literal;
import org.cossbow.feng.ast.lit.NilLiteral;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.mod.ModulePath;
import org.cossbow.feng.ast.oop.*;
import org.cossbow.feng.ast.proc.*;
import org.cossbow.feng.ast.stmt.*;
import org.cossbow.feng.ast.struct.StructureDefinition;
import org.cossbow.feng.ast.struct.StructureField;
import org.cossbow.feng.ast.var.*;
import org.cossbow.feng.dag.DAGGraph;
import org.cossbow.feng.util.*;
import org.cossbow.feng.util.Optional;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.cossbow.feng.ast.dcl.ReferKind.PHANTOM;
import static org.cossbow.feng.ast.dcl.ReferKind.STRONG;
import static org.cossbow.feng.util.ErrorUtil.*;

/**
 * Generates C11 code from a Feng AST (post semantic analysis).
 * <p>
 * Output consists of a .c source file and a .h header file per module.
 * The runtime support header (c/Header.h) is copied alongside the output.
 */
public class CGenerator implements Generator {
    private final AnalyseSymbolTable table;
    private Appendable out;
    private final boolean header;   // true = header file, false = source file
    private final boolean debug;
    private final boolean memchk;   // enables FENG_DEBUG_MEMORY
    public Set<String> testFilter;
    private Map<ModulePath, AnalyseSymbolTable> importedTables;

    public CGenerator(AnalyseSymbolTable table,
                      Appendable out, boolean header,
                      boolean debug) {
        this.table = table;
        this.out = out;
        this.header = header;
        this.debug = debug;
        memchk = Boolean.parseBoolean(System.getProperty("feng.memchk"));
    }

    public CGenerator(AnalyseSymbolTable table,
                      Appendable out,
                      boolean debug) {
        this(table, out, false, debug);
    }

    /**
     * Provide access to imported modules' symbol tables for cross-module generic function lookup.
     */
    public void importedTables(Map<ModulePath, AnalyseSymbolTable> importedTables) {
        this.importedTables = importedTables;
    }

    // ---- Factory ----

    /**
     * Path to the C runtime header and code bundled in resources.
     */
    static final String[] BaseDeps = {"Header.h", "builtin.h", "builtin.c"};
    static final String mainFile = "c/Main.c";

    public static final Factory FACTORY = new Factory() {
        @Override
        public Generator create(AnalyseSymbolTable ast, Appendable out, boolean header, boolean debug) {
            return new CGenerator(ast, out, header, debug);
        }

        @Override
        public String extension() {
            return ".c";
        }

        @Override
        public void copyBaseHeader(Path dir) {
            for (String s : BaseDeps) {
                try (var is = getResource("c/" + s)) {
                    var target = dir.resolve(s);
                    Files.deleteIfExists(target);
                    Files.copy(is, target);
                } catch (IOException e) {
                    throw ErrorUtil.sneaky(e);
                }
            }
        }

        @Override
        public String compiler() {
            return "cc";
        }

        @Override
        public String version() {
            return "c11";
        }
    };

    private static InputStream getResource(String res) {
        var cl = Thread.currentThread().getContextClassLoader();
        return new BufferedInputStream(Objects.requireNonNull(
                cl.getResourceAsStream(res)));
    }


    // When true, DerivedTypeDeclarer fields are written with 'struct' prefix
    // because inside a struct body the typedef name may not be in scope yet.
    private boolean insideStructBody = false;
    // exception handling: inside try-with-finally → transform return to flag+goto
    private boolean insideTryFinally = false;
    // exception handling: nesting counter for unique finally labels
    private int tryFinallyDepth = 0;

    // ---- dedup / deferred emission ----
    // Types and functions needed by code are registered first, then emitted
    // at file scope before the code that uses them.
    // #ifndef guards prevent duplicate definitions across module headers.

    private final Set<String> emittedTypedefs = new HashSet<>();
    private final Set<String> emittedCleanups = new HashSet<>();
    // runtime global variable initializers (emitted in a constructor function)
    private final List<Runnable> globalInits = new ArrayList<>();
    // per-type cleanup: track which array SRef types need cleanup functions
    private final Set<TypeDeclarer> cleanupTypes = new LinkedHashSet<>();
    // concrete types deferred because they need complete struct definitions
    // (emitted by declareConcreteTypesDeferred after classesDefinition)
    private final List<ConcreteTypeInst> deferredConcreteTypes = new ArrayList<>();
    // concrete generic instantiations — read from AnalyseSymbolTable (populated by Monomorphization)
    // track which func instantiations already have forward declarations
    private final Set<FuncInstantiation> declaredFuncInsts = new HashSet<>();
    // track which method instantiations have been emitted
    private final Set<MethodInstantiation> emittedMethodInsts = new HashSet<>();
    // monomorphization context: current TypeParameter → TypeDeclarer mapping.
    // Set before generating a concrete instantiation's body. null means no generic context.
    private Map<TypeParameter, TypeDeclarer> currentTypeMap;

    /**
     * Build a type map from positional TypeParameters → TypeArguments.
     */
    private static Map<TypeParameter, TypeDeclarer> buildTypeMap(TypeParameters params, TypeArguments args) {
        if (args == null || params.isEmpty() || args.isEmpty()) return null;
        var map = new LinkedHashMap<TypeParameter, TypeDeclarer>();
        int size = Math.min(params.size(), args.size());
        for (int i = 0; i < size; i++) {
            map.put(params.get(i), args.get(i));
        }
        return map;
    }

    /**
     * Enter a monomorphization context — all type writes within body resolve GenericTypeDeclarer
     * via the type map built from params→args.
     */
    private void withMono(TypeParameters params, TypeArguments args, Runnable body) {
        var saved = currentTypeMap;
        currentTypeMap = buildTypeMap(params, args);
        try {
            body.run();
        } finally {
            currentTypeMap = saved;
        }
    }

    /**
     * Enter a monomorphization context that combines class-level and method-level
     * type parameters so that both are resolvable inside the body.
     */
    private void withMonoComposed(TypeParameters classParams, TypeArguments classArgs,
                                  TypeParameters methodParams, TypeArguments methodArgs,
                                  Runnable body) {
        var combined = new LinkedHashMap<TypeParameter, TypeDeclarer>();
        if (classArgs != null && !classParams.isEmpty()) {
            int size = Math.min(classParams.size(), classArgs.size());
            for (int i = 0; i < size; i++) combined.put(classParams.get(i), classArgs.get(i));
        }
        if (methodArgs != null && !methodParams.isEmpty()) {
            int size = Math.min(methodParams.size(), methodArgs.size());
            for (int i = 0; i < size; i++) combined.put(methodParams.get(i), methodArgs.get(i));
        }
        var saved = currentTypeMap;
        currentTypeMap = combined.isEmpty() ? null : combined;
        try {
            body.run();
        } finally {
            currentTypeMap = saved;
        }
    }

    /**
     * Resolve type variables using the current monomorphization context (currentTypeMap).
     * Returns the input unchanged when no mono context is active.
     */
    private TypeDeclarer monoResolve(TypeDeclarer td) {
        return resolveFromMap(td, currentTypeMap);
    }


    /**
     * register array SRef cleanup type for later emission
     */
    private void addCleanupType(TypeDeclarer elem) {
        elem = monoResolve(elem);
        // unresolved type variables (generic definition pre-scan) — skip:
        // the concrete instantiation registers its own resolved element type
        if (elem.hasTypeVar()) return;
        cleanupTypes.add(elem);
    }

    // pending class cleanup functions (must be at file scope)
    private final List<Runnable> classCleanups = new ArrayList<>();
    // forward declarations for array cleanup functions (deferred until typedefs are emitted)
    private final List<Runnable> classCleanupForwards = new ArrayList<>();

    /**
     * emit file-scope class cleanup functions
     */
    private void emitClassCleanups() {
        while (!classCleanups.isEmpty() || !classCleanupForwards.isEmpty()) {
            // forward declarations first — bodies may reference each other
            var fwds = new ArrayList<>(classCleanupForwards);
            classCleanupForwards.clear();
            for (var r : fwds) r.run();
            // copy to avoid ConcurrentModificationException when run() adds more
            var batch = new ArrayList<>(classCleanups);
            classCleanups.clear();
            for (var r : batch) r.run();
        }
    }

    /**
     * Register the cleanup function for a resolved strong-ref field type and return
     * its name, or null when the generic {@code Feng$cleanup_sref} applies.
     */
    private String fieldCleanupFn(TypeDeclarer ft) {
        if (ft instanceof ArrayTypeDeclarer atd) {
            var elem = monoResolve(atd.element());
            var ek = typeKey(elem);
            // ensure the array cleanup gets defined even when no local
            // variable of this array type exists
            addCleanupType(elem);
            // forward-declare: deferred until array typedefs are available
            // (skip unresolved type vars — concrete instantiations register
            //  their own resolved element types)
            if (!elem.hasTypeVar() && emittedCleanups.add("fwd_arr_" + ek)) {
                classCleanupForwards.add(() ->
                        write("static inline void Feng$cleanup_arr_").write(ek)
                                .write("(Feng$ArraySRef_").write(ek)
                                .write(" *p)").endStmt());
            }
            return "Feng$cleanup_arr_" + ek;
        }
        if (ft instanceof DerivedTypeDeclarer dtd
                && dtd.def() instanceof ClassDefinition fcd && !fcd.isFinal()) {
            var fck = "Feng$cleanup_" + typeKey(ft);
            if (fcd.generic().isEmpty()) needClassCleanup(fcd, fck);
            else needConcreteClassCleanup(fcd, dtd.derivedType(), fck);
            return fck;
        }
        return null;
    }

    /**
     * register class cleanup for later file-scope emission.
     * generates cascade release for all strong-ref fields in the hierarchy.
     */
    private void needClassCleanup(ClassDefinition cd, String ck) {
        if (!emittedCleanups.add(ck)) return;
        classCleanupForwards.add(() ->
                write("static inline void ").write(ck).write("(")
                        .write(cd.symbol()).write(" **p)").endStmt());
        // eagerly register nested field cleanups so all forward declarations
        // are emitted before any cleanup body that references them
        for (var cf : cd.allFields().values()) {
            var ft = cf.type();
            var fr = ft.maybeRefer();
            if (fr.has() && fr.get().isKind(STRONG)) fieldCleanupFn(ft);
        }
        classCleanups.add(() -> {
            write("static inline void ").write(ck).write("(")
                    .write(cd.symbol()).write(" **p) {").indent();
            write("if (*p && Feng$dec(*p)) {").indent();
            // cascade: release all strong-ref fields in class hierarchy
            for (var cf : cd.allFields().values()) {
                var ft = cf.type();
                var fr = ft.maybeRefer();
                if (fr.none() || !fr.get().isKind(STRONG)) continue;
                var fck = fieldCleanupFn(ft);
                write(fck != null ? fck : "Feng$cleanup_sref")
                        .write("(&(*p)->").write(cf.name()).write(")").endStmt();
            }
            // resource free (if exists)
            cd.resourceFree().use(rf -> {
                write(cd.symbol()).write(rf.name()).write("(*p)").endStmt();
            });
            write("Feng$free(*p)").endStmt();
            dedent().write('}').newLine();
            dedent().write('}').newLine();
        });
    }

    /**
     * Concrete-instantiation variant of {@link #needClassCleanup} for generic classes:
     * the parameter struct uses the mangled concrete name and field types are resolved
     * positionally from the instantiation's type arguments.
     */
    private void needConcreteClassCleanup(ClassDefinition cd, DerivedType dt, String ck) {
        if (!emittedCleanups.add(ck)) return;
        classCleanupForwards.add(() ->
                write("static inline void ").write(ck).write("(")
                        .writeMangledName(dt).write(" **p)").endStmt());
        // eagerly register nested field cleanups (resolved to concrete types)
        for (var cf : cd.allFields().values()) {
            var ft = resolveFromMap(cf.type(), buildTypeMap(cd.generic(), dt.generic()));
            var fr = ft.maybeRefer();
            if (fr.has() && fr.get().isKind(STRONG)) fieldCleanupFn(ft);
        }
        classCleanups.add(() -> {
            write("static inline void ").write(ck).write("(")
                    .writeMangledName(dt).write(" **p) {").indent();
            write("if (*p && Feng$dec(*p)) {").indent();
            for (var cf : cd.allFields().values()) {
                var ft = resolveFromMap(cf.type(), buildTypeMap(cd.generic(), dt.generic()));
                var fr = ft.maybeRefer();
                if (fr.none() || !fr.get().isKind(STRONG)) continue;
                var fck = fieldCleanupFn(ft);
                write(fck != null ? fck : "Feng$cleanup_sref")
                        .write("(&(*p)->").write(cf.name()).write(")").endStmt();
            }
            cd.resourceFree().use(rf -> {
                writeMangledName(dt).write(rf.name()).write("(*p)").endStmt();
            });
            write("Feng$free(*p)").endStmt();
            dedent().write('}').newLine();
            dedent().write('}').newLine();
        });
    }

    /**
     * emit per-type array SRef cleanup functions at file scope
     */
    /**
     * Emit forward declarations for all cleanup functions (array + class).
     * Must be called AFTER struct/typedef emissions but BEFORE classMethods/functionDefinition
     * so that FENG$DEC references in method bodies can resolve the function names.
     */
    private void emitCleanupForwardDecls() {
        // pre-register nested cleanups (no output): element cascade bodies may
        // call class cleanups (Feng$cleanup_SPtr) whose forward declarations
        // must be emitted before the array cleanup bodies
        {
            var seen = new HashSet<TypeDeclarer>();
            boolean grew = true;
            while (grew) {
                grew = false;
                for (var elem : new ArrayList<>(cleanupTypes)) {
                    if (seen.add(elem)) {
                        preRegisterElemCleanup(elem);
                        grew = true;
                    }
                }
            }
        }
        // flush pending class-cleanup forward declarations —
        // array cleanup bodies below may call them (Feng$cleanup_SPtr)
        {
            var fwds = new ArrayList<>(classCleanupForwards);
            classCleanupForwards.clear();
            for (var r : fwds) r.run();
        }
        // forward-declare class cleanups that array cleanups may reference
        for (var elem : cleanupTypes) {
            if (elem instanceof DerivedTypeDeclarer dtd
                    && dtd.refer().has() // pointer elements only — value elements cascade per-field
                    && dtd.def() instanceof ClassDefinition cd && !cd.isFinal()
                    && !isSimple(elem) && !isArraySRef(elem)) {
                var fck = "Feng$cleanup_" + typeKey(elem);
                if (emittedCleanups.add("fwd_" + fck)) {
                    write("static inline void ").write(fck).write("(");
                    if (dtd.derivedType().generic().isEmpty()) write(cd.symbol());
                    else writeMangledName(dtd.derivedType());
                    write(" **p);").newLine();
                }
            }
        }
        // forward-declare all array cleanups (they may call each other)
        for (var elem : cleanupTypes) {
            var ek = typeKey(elem);
            if (emittedCleanups.contains("arr_" + ek)) continue;
            if (!emittedCleanups.add("fwd_arr_" + ek)) continue;
            write("static inline void Feng$cleanup_arr_").write(ek)
                    .write("(Feng$ArraySRef_").write(ek).write(" *p)").endStmt();
        }
    }

    private void emitCleanupFunctions() {
        // Emit forward declarations first (in case any were added during classMethods)
        emitCleanupForwardDecls();

        // Now emit the function bodies
        var extraCleanups = new ArrayList<Runnable>();
        for (var elem : cleanupTypes) {
            if (elem instanceof DerivedTypeDeclarer dtd
                    && dtd.refer().has()
                    && dtd.def() instanceof ClassDefinition cd && !cd.isFinal()
                    && !isSimple(elem) && !isArraySRef(elem)) {
                var fck = "Feng$cleanup_" + typeKey(elem);
                extraCleanups.add(() -> {
                    needClassCleanup(cd, fck);
                });
            }
        }
        for (var elem : cleanupTypes) {
            var ek = typeKey(elem);
            var ck = "Feng$ArraySRef_" + ek;
            if (!emittedCleanups.add("arr_" + ek)) continue;
            var guard = guardName("FENG_FUNC_cleanup_arr_" + ek);
            write("#ifndef ").write(guard).newLine();
            write("#define ").write(guard).newLine();
            write("static inline void Feng$cleanup_arr_").write(ek)
                    .write("(").write(ck).write(" *p) {").indent();
            write("if (p->$values && Feng$dec(p->$values)) {").indent();
            if (!isSimple(elem)) {
                write("for (Int64 i0 = 0; i0 < p->$length; i0++) {").indent();
                writeElemCleanup(elem, "p->$values[i0]", 1);
                dedent().write('}').newLine();
            }
            write("Feng$free(p->$values)").endStmt();
            dedent().write('}').newLine();
            dedent().write('}').newLine();
            write("#endif").newLine();
        }
        // register class cleanups that were deferred (forward-declared above)
        for (var r : extraCleanups) r.run();
        cleanupTypes.clear();
    }

    /**
     * Registration-only counterpart of {@link #writeElemCleanup}: ensures all
     * cleanups an element cascade will call are registered (forward
     * declarations + nested array cleanup types) without emitting output.
     */
    private void preRegisterElemCleanup(TypeDeclarer elem) {
        if (isSimple(elem)) return;
        if (isArraySRef(elem)) {
            addCleanupType(((ArrayTypeDeclarer) elem).element());
            return;
        }
        if (elem instanceof ArrayTypeDeclarer atd && atd.refer().none()) {
            preRegisterElemCleanup(atd.element());
            return;
        }
        if (elem instanceof DerivedTypeDeclarer dtd
                && dtd.def() instanceof ClassDefinition cd) {
            if (dtd.refer().none()) {
                // class value element: cascade registers its strong-ref fields
                for (var cf : cd.allFields().values()) {
                    var ft = cf.type();
                    var fr = ft.maybeRefer();
                    if (fr.has() && fr.get().isKind(STRONG)) fieldCleanupFn(ft);
                }
                return;
            }
            if (!cd.isFinal()) {
                var ck = "Feng$cleanup_" + typeKey(elem);
                if (dtd.derivedType().generic().isEmpty()) needClassCleanup(cd, ck);
                else needConcreteClassCleanup(cd, dtd.derivedType(), ck);
            }
        }
    }

    /**
     * Write cleanup statement(s) for one array element lvalue.
     * Handles SRef arrays, fixed-array values, class pointers and
     * class value elements (cascade into strong-ref fields).
     */
    private void writeElemCleanup(TypeDeclarer elem, String lv, int depth) {
        if (isSimple(elem)) return;
        if (isArraySRef(elem)) {
            // element is SRef array → call its cleanup on each item
            var iek = typeKey(((ArrayTypeDeclarer) elem).element());
            write("Feng$cleanup_arr_").write(iek)
                    .write("(&").write(lv).write(")").endStmt();
            return;
        }
        if (elem instanceof ArrayTypeDeclarer atd && atd.refer().none()) {
            // fixed-array value element: cascade into inner elements
            var iv = "i" + depth;
            write("for (Int64 ").write(iv).write(" = 0; ").write(iv)
                    .write(" < ").write(atd.len()).write("; ").write(iv)
                    .write("++) {").indent();
            writeElemCleanup(atd.element(), lv + ".$values[" + iv + "]", depth + 1);
            dedent().write('}').newLine();
            return;
        }
        if (elem instanceof DerivedTypeDeclarer dtd
                && dtd.def() instanceof ClassDefinition cd) {
            if (dtd.refer().none()) {
                // class value element: cascade strong-ref fields
                for (var cf : cd.allFields().values()) {
                    var ft = cf.type();
                    var fr = ft.maybeRefer();
                    if (fr.none() || !fr.get().isKind(STRONG)) continue;
                    var fck = fieldCleanupFn(ft);
                    write(fck != null ? fck : "Feng$cleanup_sref")
                            .write("(&").write(lv).write(".")
                            .write(cf.name()).write(")").endStmt();
                }
                return;
            }
            if (!cd.isFinal()) {
                // non-final class pointer: use per-class cleanup for cascade
                var fck = "Feng$cleanup_" + typeKey(elem);
                if (dtd.derivedType().generic().isEmpty()) needClassCleanup(cd, fck);
                else needConcreteClassCleanup(cd, dtd.derivedType(), fck);
                write("if (").write(lv).write(") ").write(fck)
                        .write("(&").write(lv).write(")").endStmt();
                return;
            }
        }
        // pointer reference → cascade Feng$dec
        write("if (").write(lv).write(") Feng$dec(").write(lv).write(")").endStmt();
    }

    /**
     * write full C type name for use in typedef bodies (no registration)
     */
    private void writeTypeName(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            write(PrimitiveName.get(ptd.primitive()));
            if (ptd.refer().has()) write('*');
        } else if (td instanceof DerivedTypeDeclarer dtd) {
            if (insideStructBody) {
                var def = dtd.def();
                if (def instanceof StructureDefinition sd) {
                    write(sd.domain().name).write(' ');
                } else {
                    write("struct ");
                }
            }
            write(dtd.derivedType());  // struct/union prefix handled above
            if (dtd.refer().has()) write('*');
        } else if (td instanceof ArrayTypeDeclarer atd) {
            if (atd.refer().none())
                write("Feng$Array_").write(typeKey(atd.element())).write('_').write(atd.len());
            else if (atd.refer().get().isKind(PHANTOM))
                write("Feng$ArrayPRef_").write(typeKey(atd.element()));
            else
                write("Feng$ArraySRef_").write(typeKey(atd.element()));
        } else {
            write(typeKey(td));
        }
    }

    private boolean emitOnce(String key) {
        return emittedTypedefs.add(key);
    }

    // ---- basic output ----

    private CGenerator write(char c) {
        try {
            out.append(c);
        } catch (IOException e) {
            io(e);
        }
        return this;
    }

    private CGenerator write(CharSequence cs) {
        try {
            out.append(cs);
        } catch (IOException e) {
            io(e);
        }
        return this;
    }

    private CGenerator write(int b) {
        return write(Integer.toString(b));
    }

    private CGenerator write(long b) {
        return write(Long.toString(b));
    }

    private CGenerator write(Identifier name) {
        if (!name.unnamed()) write('$');
        return write(name.value());
    }

    private CGenerator write(Label label) {
        return write(label.name()).write('_').write(label.id());
    }

    private static final String COMMA = ", ";

    private int dent;

    CGenerator indent() {
        dent++;
        return newLine();
    }

    CGenerator dedent() {
        dent--;
        return newLine();
    }

    CGenerator newLine() {
        write('\n');
        for (int i = 0; i < dent; i++) {
            write('\t');
        }
        return this;
    }

    void writeComment(String text) {
        write("// ").write(text).newLine();
    }

    // ---- header/footer ----

    private void definePre() {
        if (header) {
            var name = table.module.has() ? table.module.must()
                    .path().filename().toUpperCase() : "builtin";
            write("#ifndef __HEADER_").write(name).newLine();
            write("#define __HEADER_").write(name).newLine();
            return;
        }
        if (debug) write("#define FENG_DEBUG").newLine();
        if (memchk) write("#define FENG_DEBUG_MEMORY").newLine();
    }

    private void includeHeaders() {
        writeComment("base header");
        table.module.use(fm -> {
            write("#include \"Header.h\"").newLine();
            write("#include \"builtin.h\"").newLine();
            if (!header) {
                write("#include \"").write(fm.path().filename())
                        .write(".h\"").newLine();
                return;
            }
            // header file: include imported module headers
            if (!fm.imports().isEmpty()) {
                writeComment("import headers");
                for (var i : fm.imports()) {
                    write("#include \"").write(i.filename())
                            .write(".h\"").newLine();
                }
            }
        });
    }

    private void endFile() {
        if (!header) return;
        write("#endif").newLine();
    }

    // ===================================================================
    //  Type System — maps Feng types to C type names
    // ===================================================================

    /**
     * Primitive type name mapping (e.g. INT → "Int", FLOAT64 → "Float64").
     */
    public static final Map<Primitive, String> PrimitiveName =
            Arrays.stream(Primitive.values())
                    .collect(Collectors.toMap(Function.identity(),
                            p -> {
                                var s = p.code;
                                return Character.toUpperCase(s.charAt(0)) + s.substring(1);
                            }));

    private CGenerator write(Primitive p) {
        return write(PrimitiveName.get(p));
    }

    private CGenerator write(PrimitiveType pt) {
        return write(pt.primitive());
    }

    private CGenerator write(DerivedType dt) {
        if (dt.generic().isEmpty()) {
            return write(dt.symbol());
        }
        // concrete generic instantiation: mangledName includes module prefix
        return write(mangledName(dt));
    }

    private CGenerator write(GenericType gt) {
        return write(gt.name());
    }

    private CGenerator write(DefinedType t) {
        return switch (t) {
            case PrimitiveType pt -> write(pt);
            case DerivedType dt -> write(dt);
            case GenericType gt -> write(gt);
            default -> unreachable();
        };
    }

    // ---- emit helpers ----

    /**
     * 生成定长数组 typedef: typedef struct { T $values[N]; } Feng$Array_T_N;
     */
    private String emitArrayType(TypeDeclarer elem, long len) {
        var key = "Array_" + typeKey(elem) + "_" + len;
        if (!emittedTypedefs.add(key)) return key;
        var typeName = "Feng$" + key;
        var guard = guardName("FENG_TYPEDEF_" + key);
        write("#ifndef ").write(guard).newLine();
        write("#define ").write(guard).newLine();
        write("typedef struct { ");
        writeTypeName(elem);
        write(" $values[").write(len).write("]; } ").write(typeName).endStmt();
        write("#endif").newLine();
        return key;
    }

    /**
     * Returns true if this type embeds a class/struct by value, requiring a complete definition.
     */
    private boolean needsCompleteStruct(TypeDeclarer td) {
        if (td instanceof DerivedTypeDeclarer dtd) {
            var def = dtd.def();
            return dtd.refer().none()
                    && (def instanceof ClassDefinition ||
                    def instanceof StructureDefinition);
        }
        if (td instanceof ArrayTypeDeclarer atd) {
            return atd.refer().none() && needsCompleteStruct(atd.element());
        }
        if (td instanceof TupleTypeDeclarer ttd) {
            return ttd.elements().stream().anyMatch(this::needsCompleteStruct);
        }
        return false;
    }

    // ---- helper: check if a type's own typedef would be deferred ----

    /**
     * Does this element type's own typedef need to be deferred?
     * A typedef is deferred when it embeds a class/struct value type
     * (needsCompleteStruct), requiring the complete struct definition first.
     * Transitive — a typedef referencing a deferred type must itself be deferred.
     */
    private boolean arrayRefTypeDeferred(TypeDeclarer elem) {
        if (elem instanceof ArrayTypeDeclarer atd) {
            return needsCompleteStruct(atd.element())
                    || arrayRefTypeDeferred(atd.element());
        }
        if (elem instanceof TupleTypeDeclarer ttd) {
            return ttd.elements().stream().anyMatch(e ->
                    needsCompleteStruct(e) || arrayRefTypeDeferred(e));
        }
        return false;
    }

    /**
     * 生成函数原型 typedef。key 基于签名内容（Proto_Ret_P1_P2...），
     * 保证同一原型在 header/source 两个 pass 中生成同一个名字并去重。
     */
    /**
     * Compute the stable proto key for a prototype, and register it
     * for fallback typedef emission via AnalyseSymbolTable.
     */
    private String emitProtoType(Prototype pt) {
        if (pt.hasTypeVar()) return protoKey(pt);
        var key = protoKey(pt);
        if (!emittedTypedefs.add(key)) return key;
        table.pendingProtoTypedefs.add(pt);
        return key;
    }

    /**
     * 基于签名内容的稳定 key：Proto_<retKey>_<paramKey>...
     */
    private String protoKey(Prototype pt) {
        var sb = new StringBuilder("Proto");
        sb.append('_');
        if (pt.returnSet().has()) sb.append(typeKey(pt.returnSet().get()));
        else sb.append("Void");
        for (var t : pt.parameterSet().types()) {
            sb.append('_').append(typeKey(t));
        }
        return sb.toString();
    }

    // ---- simple type check ----

    private boolean isSimple(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd)
            return ptd.refer().none();
        if (td instanceof EnumTypeDeclarer) return true;
        if (td instanceof DerivedTypeDeclarer dtd) {
            if (dtd.refer().has()) return false; // reference — not simple
            var def = dtd.def();
            if (def instanceof StructureDefinition) return true;
            // value-type non-final class: simple only if no strong-ref fields + no resource
            // (cleanup just frees the array buffer, no per-element cascade)
            if (def instanceof ClassDefinition cd && !cd.isFinal()) {
                var needCascade = cd.allFields().values().stream()
                        .anyMatch(cf -> cf.type().maybeRefer().match(r -> r.isKind(STRONG)));
                return !needCascade && !cd.resource();
            }
            return false;
        }
        if (td instanceof ArrayTypeDeclarer atd) {
            var r = atd.refer();
            if (r.has()) {
                // SRef array owns its buffer (needs dec/cascade) — not simple;
                // PRef array only borrows — simple
                return !r.get().isKind(STRONG);
            }
            return isSimple(atd.element());
        }
        if (td instanceof GenericTypeDeclarer) return true;
        if (td instanceof TupleTypeDeclarer ttd)
            return ttd.elements().stream().allMatch(this::isSimple);
        return false;
    }

    /**
     * return true if element is an SRef array (needs inner cleanup call, not Feng$dec)
     */
    private boolean isArraySRef(TypeDeclarer td) {
        return td instanceof ArrayTypeDeclarer atd
                && atd.refer().has()
                && !atd.refer().get().isKind(PHANTOM);
    }

    private String typeKey(TypeDeclarer td) {
        return typeKey(td, currentTypeMap);
    }

    private String typeKey(TypeDeclarer td, Map<TypeParameter, TypeDeclarer> typeMap) {
        if (typeMap != null && td.hasTypeVar()) {
            td = resolveFromMap(td, typeMap);
        }
        if (td instanceof PrimitiveTypeDeclarer ptd) {
            var name = PrimitiveName.get(ptd.primitive());
            return ptd.refer().has() ? name + "Ptr" : name;
        }
        if (td instanceof DerivedTypeDeclarer dtd) {
            var dt = dtd.derivedType();
            String name;
            if (!dt.generic().isEmpty()) {
                name = mangledName(dt);
            } else {
                name = dt.symbol().name().value();
            }
            return dtd.refer().has() ? name + "Ptr" : name;
        }
        if (td instanceof ArrayTypeDeclarer atd) {
            var r = atd.refer();
            if (r.none()) return "Array_" + typeKey(atd.element(), typeMap) + "_" + atd.len();
            if (r.get().isKind(PHANTOM)) return "ArrayPRef_" + typeKey(atd.element(), typeMap);
            return "ArraySRef_" + typeKey(atd.element(), typeMap);
        }
        if (td instanceof GenericTypeDeclarer gtd) {
            return gtd.param().name().value();
        }
        if (td instanceof TupleTypeDeclarer ttd) {
            return tupleKey(ttd);
        }
        if (td instanceof FuncTypeDeclarer ftd) return protoKey(ftd.prototype());
        if (td instanceof EnumTypeDeclarer) return "Enum";
        return "Unknown";
    }

    /**
     * Resolve type variables using a ConcreteTypeInst typeMap.
     * Maps TypeParameter → TypeDeclarer by name-based matching.
     */
    private TypeDeclarer resolveFromMap(TypeDeclarer td, Map<TypeParameter, TypeDeclarer> typeMap) {
        if (!td.hasTypeVar() || typeMap == null || typeMap.isEmpty()) return td;
        return switch (td) {
            case GenericTypeDeclarer gtd -> {
                var tp = gtd.param();
                // Try direct lookup (TypeParameter identity)
                var resolved = typeMap.get(tp);
                if (resolved != null) yield resolved;
                // Fallback: name-based matching
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
            case FuncTypeDeclarer ftd -> {
                var pt = ftd.prototype();
                var resolvedReturn = pt.returnSet().has()
                        ? resolveFromMap(pt.returnSet().get(), typeMap)
                        : null;
                var resolvedParams = new ArrayList<TypeDeclarer>();
                for (var t : pt.parameterSet().types()) {
                    resolvedParams.add(resolveFromMap(t, typeMap));
                }
                boolean allResolved = (resolvedReturn == null || !resolvedReturn.hasTypeVar());
                for (var t : resolvedParams) if (t.hasTypeVar()) allResolved = false;
                if (!allResolved) yield ftd;
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

    // ---- type declarer writers ----

    private CGenerator write(PrimitiveTypeDeclarer td) {
        if (td.refer().none()) return write(td.primitive());
        return write(td.primitive()).write('*');
    }

    private CGenerator write(ArrayTypeDeclarer td) {
        if (td.refer().none()) {
            return write("Feng$Array_").write(typeKey(td.element()))
                    .write('_').write(td.len());
        }
        var r = td.refer().get();
        if (r.isKind(PHANTOM)) {
            return write("Feng$ArrayPRef_").write(typeKey(td.element()));
        }
        return write("Feng$ArraySRef_").write(typeKey(td.element()));
    }

    private CGenerator write(DerivedTypeDeclarer td) {
        var def = td.def();
        if (def instanceof InterfaceDefinition) {
            write("void");          // interface ref → void*
            if (td.refer().has()) write('*');
            return this;
        }
        if (def instanceof EnumDefinition)
            return write(Primitive.INT);
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

    private CGenerator write(FuncTypeDeclarer td) {
        // NamedFuncTypeDeclarer → write the prototype definition's symbol
        // plus * because function types in C are always pointers.
        // For concrete generic prototype instantiations (Task_Int),
        // fall back to the structural Feng$Proto_* format.
        if (td instanceof NamedFuncTypeDeclarer nftd) {
            if (!nftd.derivedType().generic().isEmpty()) {
                return write("Feng$").write(emitProtoType(td.prototype()));
            }
            return write(nftd.derivedType()).write('*');
        }
        // AnonFuncTypeDeclarer (should not appear after AnonFuncNormalizer,
        // but handle defensively)
        return write("Feng$").write(emitProtoType(td.prototype()));
    }

    private CGenerator write(EnumTypeDeclarer td) {
        return write(Primitive.INT);
    }

    private CGenerator write(LiteralTypeDeclarer td) {
        if (td.isInteger()) return write(Primitive.INT);
        if (td.isFloat()) return write(Primitive.FLOAT64);
        if (td.isBool()) return write(Primitive.BOOL);
        if (td.literal() instanceof StringLiteral sl) {
            return write(sl.array(Optional.of(STRONG)));
        }
        return this;
    }

    private CGenerator write(GenericTypeDeclarer td) {
        return write(td.param());
    }

    private CGenerator write(TupleTypeDeclarer td) {
        return write("Feng$").write(tupleKey(td));
    }

    // ---- tuple support ----

    private String tupleKey(TupleTypeDeclarer td) {
        return "Tuple_" + td.elements().stream()
                .map(this::typeKey).collect(Collectors.joining("_"));
    }

    private void emitTupleType(TupleTypeDeclarer td) {
        if (td.hasTypeVar()) return;
        var key = tupleKey(td);
        if (!emittedTypedefs.add(key)) return;
        var guard = guardName("FENG_TYPEDEF_" + key);
        write("#ifndef ").write(guard).newLine();
        write("#define ").write(guard).newLine();
        write("typedef struct {").indent();
        int i = 0;
        for (var et : td.elements()) {
            write(et).write(" v").write(i).endStmt();
            i++;
        }
        dedent().write("} Feng$").write(key).endStmt();
        write("#endif").newLine();
    }

    // ---- declare types from ConcreteTypeInst (DAG topological order) ----

    /**
     * Emit typedefs for all concrete types discovered by Monomorphization,
     * in DAG topological order (dependencies first).
     * <p>
     * Types that embed class value types (needing complete struct definitions)
     * are emitted by {@link #declareConcreteTypesDeferred()} after classesDefinition().
     */
    private void declareConcreteTypes() {
        // Process all ConcreteTypeInsts in DAG topological order.
        // The DAG guarantees dependencies come before dependents.
        for (var cti : table.concreteTypeInsts) {
            var def = cti.def();
            if (def instanceof FixedArrayDefinition fad) {
                var elemTd = cti.typeMap().get(fad.elementParam());
                boolean needsDeferred = elemTd != null
                        && (needsCompleteStruct(elemTd) || arrayRefTypeDeferred(elemTd));
                if (needsDeferred) {
                    // Track for deferred emission after classesDefinition()
                    deferredConcreteTypes.add(cti);
                } else {
                    declareFixedArray(fad, cti.typeMap());
                }
            } else if (def instanceof ArrayRefDefinition ard) {
                var elemTd = cti.typeMap().get(ard.elementParam());
                boolean needsDeferred = elemTd != null && arrayRefTypeDeferred(elemTd);
                if (needsDeferred) {
                    deferredConcreteTypes.add(cti);
                } else {
                    declareArrayRef(ard, cti.typeMap());
                }
            } else if (def instanceof TupleDefinition td) {
                var resolvedElements = new ArrayList<TypeDeclarer>();
                for (var tp : td.elementParams()) {
                    var resolved = cti.typeMap().get(tp);
                    if (resolved != null) resolvedElements.add(resolved);
                }
                boolean needsDeferred = resolvedElements.stream()
                        .anyMatch(t -> needsCompleteStruct(t) || arrayRefTypeDeferred(t));
                if (needsDeferred) {
                    deferredConcreteTypes.add(cti);
                } else {
                    declareTuple(td, cti.typeMap());
                }
            } else if (def instanceof ClassDefinition cd && !cd.generic().isEmpty()) {
                var resolvedArgs = new ArrayList<String>();
                for (var tp : cd.generic()) {
                    var resolved = cti.typeMap().get(tp);
                    if (resolved != null) resolvedArgs.add(typeKey(resolved));
                }
                var sb = new StringBuilder();
                cd.symbol().module().use(m -> sb.append(m).append('$'));
                sb.append(cd.symbol().name().value()).append('_')
                        .append(String.join("_", resolvedArgs));
                var mName = sb.toString();
                if (emittedTypedefs.add(mName)) {
                    write("typedef struct ").write(mName)
                            .write(' ').write(mName).endStmt();
                }
                // Register cleanup for class fields so cleanup functions are emitted
                var prevMap = currentTypeMap;
                currentTypeMap = cti.typeMap();
                try {
                    for (var f : cd.allFields().values()) {
                        registerClassCleanup(f.type());
                    }
                } finally {
                    currentTypeMap = prevMap;
                }
            } else if (def instanceof PrototypeDefinition pd) {
                var prevMap = currentTypeMap;
                currentTypeMap = cti.typeMap();
                try {
                    emitProtoType(pd.prototype());
                } finally {
                    currentTypeMap = prevMap;
                }
            } else if (def instanceof InterfaceDefinition id && !id.generic().isEmpty()) {
                // Register interface method signature types so cleanup functions are emitted
                var prevMap = currentTypeMap;
                currentTypeMap = cti.typeMap();
                try {
                    for (var im : id.allMethods()) {
                        im.prototype().returnSet().use(this::registerClassCleanup);
                        for (var t : im.prototype().parameterSet().types()) {
                            registerClassCleanup(t);
                        }
                    }
                } finally {
                    currentTypeMap = prevMap;
                }
            }
        }
    }

    /**
     * Emit deferred concrete types that need complete struct definitions
     * (types embedding class values). Must be called AFTER classesDefinition().
     */
    private void declareConcreteTypesDeferred() {
        for (var cti : deferredConcreteTypes) {
            var def = cti.def();
            if (def instanceof FixedArrayDefinition fad) {
                declareFixedArray(fad, cti.typeMap());
            } else if (def instanceof ArrayRefDefinition ard) {
                declareArrayRef(ard, cti.typeMap());
            } else if (def instanceof TupleDefinition td) {
                declareTuple(td, cti.typeMap());
            }
        }
        deferredConcreteTypes.clear();
    }

    /**
     * Emit typedef for a fixed-length array: typedef struct { T $values[N]; Int64 $length; } Feng$Array_T_N;
     */
    private void declareFixedArray(FixedArrayDefinition def,
                                   Map<TypeParameter, TypeDeclarer> typeMap) {
        var elementTd = typeMap.get(def.elementParam());
        if (elementTd == null) return;
        var key = "Array_" + typeKey(elementTd) + "_" + def.length();
        if (!emittedTypedefs.add(key)) return;
        var typeName = "Feng$" + key;
        var guard = guardName("FENG_TYPEDEF_" + key);
        write("#ifndef ").write(guard).newLine();
        write("#define ").write(guard).newLine();
        write("typedef struct { ");
        writeTypeName(elementTd);
        write(" $values[").write(def.length()).write("]; } ").write(typeName).endStmt();
        write("#endif").newLine();
    }

    /**
     * Emit typedef for a reference-counted array: SRef or PRef.
     * SRef: typedef struct { T* $values; Int64 $length; } Feng$ArraySRef_T;
     * PRef: typedef struct { T* $values; Int64 $length; } Feng$ArrayPRef_T;
     */
    private void declareArrayRef(ArrayRefDefinition def,
                                 Map<TypeParameter, TypeDeclarer> typeMap) {
        var elementTd = typeMap.get(def.elementParam());
        if (elementTd == null) return;
        String prefix = def.phantom() ? "ArrayPRef" : "ArraySRef";
        var key = prefix + "_" + typeKey(elementTd);
        if (!emittedTypedefs.add(key)) return;
        var typeName = "Feng$" + key;
        var guard = guardName("FENG_TYPEDEF_" + key);
        write("#ifndef ").write(guard).newLine();
        write("#define ").write(guard).newLine();
        write("typedef struct { ");
        writeTypeName(elementTd);
        write("* $values; Int64 $length; } ").write(typeName).endStmt();
        write("#endif").newLine();
        // SRef arrays need cleanup function registration
        if (!def.phantom()) {
            addCleanupType(elementTd);
        }
    }

    /**
     * Emit typedef for a tuple: typedef struct { T0 v0; T1 v1; ... } Feng$Tuple_T0_T1;
     */
    private void declareTuple(TupleDefinition def,
                              Map<TypeParameter, TypeDeclarer> typeMap) {
        var resolvedElements = new ArrayList<TypeDeclarer>();
        for (var tp : def.elementParams()) {
            var resolved = typeMap.get(tp);
            if (resolved == null) resolved = Primitive.INT.declarer();
            resolvedElements.add(resolved);
        }
        if (resolvedElements.stream().anyMatch(TypeDeclarer::hasTypeVar)) return;
        var key = "Tuple_" + resolvedElements.stream()
                .map(this::typeKey).collect(Collectors.joining("_"));
        if (!emittedTypedefs.add(key)) return;
        var guard = guardName("FENG_TYPEDEF_" + key);
        write("#ifndef ").write(guard).newLine();
        write("#define ").write(guard).newLine();
        write("typedef struct {").indent();
        int i = 0;
        for (var et : resolvedElements) {
            write(et).write(" v").write(i).endStmt();
            i++;
        }
        dedent().write("} Feng$").write(key).endStmt();
        write("#endif").newLine();
    }

    private CGenerator write(VoidTypeDeclarer td) {
        return write("void");
    }

    private CGenerator write(TypeDeclarer td) {
        td = monoResolve(td);
        return switch (td) {
            case PrimitiveTypeDeclarer ee -> write(ee);
            case ArrayTypeDeclarer ee -> write(ee);
            case DerivedTypeDeclarer ee -> write(ee);
            case FuncTypeDeclarer ee -> write(ee);
            case EnumTypeDeclarer ee -> write(ee);
            case LiteralTypeDeclarer ee -> write(ee);
            case GenericTypeDeclarer ee -> write(ee);
            case TupleTypeDeclarer ee -> write(ee);
            case VoidTypeDeclarer ee -> write(ee);
            case null, default -> unreachable();
        };
    }

    // ---- generics (C has no templates, use monomorphization) ----

    private CGenerator write(TypeParameter tp) {
        return write(tp.name());
    }

    private CGenerator write(TypeParameters tps) {
        return this; // no-op — generic template not emitted
    }

    private CGenerator write(TypeArguments tas) {
        return this; // no-op — type args are embedded in mangled name
    }

    /**
     * Produce the C identifier suffix for a concrete generic instantiation.
     * e.g., Box`int` → "Box_Int", Pair`int,bool` → "Pair_Int_Bool"
     */
    /**
     * Mangled name for a concrete generic type, including the module prefix.
     * E.g. "test$aad$HashMap_Int_Int", "std$map$HashMap_Int_Int".
     * Used everywhere a unique C identifier is needed for a generic instantiation.
     */
    private String mangledName(DerivedType dt) {
        assert !dt.generic().isEmpty();
        var sb = new StringBuilder();
        dt.symbol().module().use(m -> sb.append(m).append('$'));
        sb.append(dt.name().value()).append('_');
        sb.append(dt.generic().stream()
                .map(this::typeKey)
                .collect(Collectors.joining("_")));
        return sb.toString();
    }

    /**
     * Convert a name to a valid C preprocessor guard identifier.
     * '$' is not allowed in C preprocessor identifiers, so replace with '_'.
     */
    private static String guardName(String name) {
        return name.replace('$', '_');
    }

    /**
     * Write the mangled C symbol name (without struct/union prefix).
     */
    private CGenerator writeMangledName(DerivedType dt) {
        if (dt.generic().isEmpty()) return write(dt.symbol());
        return write(mangledName(dt));
    }

    /**
     * Write a method reference in the same format as its implementation.
     * Non-generic: write(class.symbol)write(methodName) = pkg$Class$method
     * Concrete generic: writeMangledName(dt).write('$').write(methodName) = pkg$Class_Args$$method
     */
    private CGenerator writeMethodRef(DerivedType dt, Identifier methodName) {
        if (dt.generic().isEmpty()) {
            return write(dt.symbol()).write(methodName);
        } else {
            return writeMangledName(dt).write('$').write(methodName);
        }
    }

    /**
     * Write an interface method reference — finds the implementing ancestor and
     * writes the reference in the same format as the implementation.
     */
    private CGenerator writeIfaceMethodRef(ClassDefinition cd, DerivedType dt, InterfaceMethod im) {
        var impl = cd;
        while (!impl.methods().exists(im.name()) && impl.parent().has()
                && impl.parent().must() != ClassDefinition.ObjectClass) {
            impl = impl.parent().must();
        }
        var implDt = ancestorDt(cd, dt, impl);
        return writeMethodRef(implDt, im.name());
    }

    // ---- common helpers ----

    private <T> void joinByComma(Iterable<T> s, Consumer<T> w) {
        var first = true;
        for (var t : s) {
            if (first) first = false;
            else write(", ");
            w.accept(t);
        }
    }

    private CGenerator endStmt() {
        return write(";").newLine();
    }

    private CGenerator write(Symbol s) {
        s.module().use(mp -> write(mp.toString()));
        write(s.name());
        return this;
    }

    private CGenerator write(Symbol symbol, Prototype pt) {
        return write(() -> write(symbol), pt);
    }

    private CGenerator write(Runnable nameToken, Prototype pt) {
        var ps = pt.parameterSet();
        pt.returnSet().use(this::write, () -> write("void"));
        write(' ');
        nameToken.run();
        write('(').write(ps).write(')');
        return this;
    }

    // ---- parameter set ----

    CGenerator write(ParameterSet ps) {
        joinByComma(ps, p -> {
            if (!(p instanceof FixedParameter fp)) {
                unreachable();
                return;
            }
            var v = fp.var();
            if (v.none()) write(fp.type());
            else declare(v.get());
        });
        return this;
    }

    // ---- variables ----

    private CGenerator varName(Variable v) {
        if (v instanceof GlobalVariable gv) {
            write(gv.symbol());
        } else {
            write(v.name());
        }
        return write('_').write(v.id());
    }

    private CGenerator declare(Variable v) {
        return write(v.type().must()).write(' ').varName(v);
    }

    private CGenerator declareVar(Variable v) {
        var t = v.type().must();
        // pre-register per-type cleanup before writing declaration
        var ref = t.maybeRefer();
        String cleanupFn = null;
        if (ref.has() && ref.get().isKind(STRONG)) {
            // handles both plain classes and concrete generic instantiations
            cleanupFn = classCleanupFnFor(t);
        }

        declare(v);
        // attach cleanup attribute
        if (ref.has() && ref.get().isKind(STRONG)) {
            if (t instanceof ArrayTypeDeclarer atd)
                write(" FENG$DEC(Feng$cleanup_arr_").write(typeKey(monoResolve(atd.element()))).write(")");
            else if (cleanupFn != null)
                write(" FENG$DEC(").write(cleanupFn).write(")");
            else
                write(" FENG$DEC(Feng$cleanup_sref)");
        }
        write(" = ");
        v.value().use(e -> writeValue(e, t), () -> {
            if (t instanceof ArrayTypeDeclarer) write("{}");
            else if (t.maybeRefer().has()) write("NULL");
            else write("{}");
        });
        return endStmt();
    }

    // ---- default value ----

    private CGenerator defaultValue(TypeDeclarer td) {
        if (td instanceof FuncTypeDeclarer) return write("NULL");
        if (td.maybeRefer().has()) return write("NULL");
        if (td instanceof PrimitiveTypeDeclarer ||
                td instanceof GenericTypeDeclarer) return write('0');
        if (td instanceof DerivedTypeDeclarer dtd) {
            var def = dtd.def();
            if (def instanceof ClassDefinition cd && !cd.isFinal())
                return write("(").write(dtd).write("){.$meta = ")
                        .writeMetaBaseRef(cd, dtd.derivedType()).write("}");
        }
        return write("{}");
    }

    // ===================================================================
    //  Expressions — write each Feng expression node as C syntax
    // ===================================================================

    /**
     * Converts value to target type, handling ref wrapping and casts.
     */
    private CGenerator writeValue(Expression v, TypeDeclarer t) {
        if (v instanceof LiteralExpression le) return writeLiteral(le, t);
        var r = t.maybeRefer();
        if (r.none()) return write(v);
        if (r.get().isKind(PHANTOM)) return referPhantom(v, t);
        return castRef(v, t);
    }

    private CGenerator castRef(Expression v, TypeDeclarer t) {
        var rt = v.resultType.must();
        if (t.baseTypeSame(rt)) {
            // SRef array → PRef array: wrap .$values & .$length fields
            if (t instanceof ArrayTypeDeclarer tat && rt instanceof ArrayTypeDeclarer rat
                    && tat.refer().has() && rat.refer().has()
                    && tat.refer().get().isKind(PHANTOM)
                    && !rat.refer().get().isKind(PHANTOM)) {
                return write('(').write(t).write("){").write(v)
                        .write(".$values, ").write(v).write(".$length}");
            }
            // same-type strong ref copy: Feng$inc unless source is unbound
            // (unbound = temporary/new expression that transfers ownership)
            var needInc = t.maybeRefer().match(r -> r.isKind(STRONG))
                    && !v.unbound()
                    && !(v instanceof IsExpression);
            // SRef array struct: inc the $values pointer, copy {$values, $length}
            if (needInc && t instanceof ArrayTypeDeclarer tat) {
                var ek = typeKey(tat.element());
                write("(Feng$ArraySRef_").write(ek).write("){(")
                        .write(tat.element()).write(" *)Feng$inc((")
                        .write(v).write(").$values), (").write(v).write(").$length}");
                return this;
            }
            if (needInc) write("Feng$inc(");
            write(v);
            if (needInc) write(")");
            return this;
        }
        // non-final class upcast: B* → A* (B extends A)
        if (rt instanceof DerivedTypeDeclarer rdt && t instanceof DerivedTypeDeclarer tdt
                && rdt.def() instanceof ClassDefinition rcd && !rcd.isFinal()
                && tdt.def() instanceof ClassDefinition tcd && !tcd.isFinal()
                && isSubclass(rcd, tcd)) {
            // flat layout: simple pointer cast; Feng$inc unless source is unbound or target phantom
            var needInc = t.maybeRefer().match(r -> r.isKind(STRONG))
                    && !v.unbound() && !(v instanceof IsExpression);
            if (needInc) write("Feng$inc(");
            write("((");
            // use mangled name for concrete generic types (Box_IntPtr, not Box)
            if (tdt.derivedType().generic().isEmpty()) write(tcd.symbol());
            else writeMangledName(tdt.derivedType());
            write(" *)(");
            write(v);
            write("))");
            if (needInc) write(")");
            return this;
        }
        if (t instanceof ArrayTypeDeclarer at) {
            if (rt.isNil()) {
                // nil → array ref struct: empty {NULL, 0} literal
                return write('(').write(t).write("){NULL, 0}");
            }
            // reinterpret source as array of at.element():
            //   {(E*)<data>, <byteSize>/sizeof(E)}   (cf. C++ Feng$mapU2A/A2A)
            var needInc = at.refer().match(r -> r.isKind(STRONG))
                    && !v.unbound() && !(v instanceof IsExpression);
            write('(').write(t).write("){(").write(at.element()).write(" *)");
            if (needInc) write("Feng$inc(");
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
                write("(sizeof(").write(art.element()).write(')');
                if (art.refer().none()) write("*").write(art.len());
                else write("*(").write(v).write(").$length");
                write(')');
            } else {
                write("sizeof(").baseTypeSymbol(rt).write(')');
            }
            write("/sizeof(").write(at.element()).write(')');
            return write('}');
        }
        if (rt instanceof ArrayTypeDeclarer) {
            // array → single ref: (U*)data   (cf. C++ Feng$mapA2U)
            var needInc = t.maybeRefer().match(r -> r.isKind(STRONG))
                    && !v.unbound() && !(v instanceof IsExpression);
            write("((").baseTypeSymbol(t).write(" *)");
            if (needInc) write("Feng$inc(");
            else write("(void*)");
            write('(').write(v).write(").$values");
            if (needInc) write(')');
            return write(')');
        }
        // unrelated data-type reinterpret: (T*)(void*)ptr  (primitive/struct only)
        if (!rt.isNil() && t.maybeRefer().has() && isDataType(t)) {
            var needInc = t.maybeRefer().match(r -> r.isKind(STRONG))
                    && !v.unbound() && !(v instanceof IsExpression);
            write("((").baseTypeSymbol(t).write(" *)");
            if (needInc) write("Feng$inc(");
            else write("(void*)");
            if (rt.maybeRefer().none()) write("&(").write(v).write(')');
            else write('(').write(v).write(')');
            if (needInc) write(')');
            return write(')');
        }
        return write(v);
    }

    /**
     * primitive or plain struct — safe target for pointer reinterpretation
     */
    private boolean isDataType(TypeDeclarer t) {
        return t instanceof PrimitiveTypeDeclarer
                || (t instanceof DerivedTypeDeclarer dtd
                && dtd.def() instanceof StructureDefinition);
    }

    private CGenerator referPhantom(Expression v, TypeDeclarer t) {
        var vt = v.resultType.must();
        if (vt.maybeRefer().has()) {
            return castRef(v, t);
        }
        if (t.baseTypeSame(vt)) {
            if (!(vt instanceof ArrayTypeDeclarer avt)) {
                if (vt.maybeRefer().none()) {
                    if (v.unbound()) {
                        // rvalue → array compound literal: an lvalue whose
                        // lifetime is the enclosing block, decays to pointer
                        return write('(').write(vt).write("[1]){")
                                .write(v).write('}');
                    }
                    write('&');
                }
                return write(v);
            }
            // fixed array → wrap in per-type Feng$ArrayPRef struct
            var elemType = avt.element();
            return write("(Feng$ArrayPRef_").write(typeKey(elemType))
                    .write("){(void*)").write(v).write(".$values, ")
                    .write(avt.len()).write('}');
        }
        // value-type subclass → phantom ref of base class (e.g., B → &A)
        if (t instanceof DerivedTypeDeclarer tdt && tdt.refer().match(r -> r.isKind(PHANTOM))
                && tdt.def() instanceof ClassDefinition tcd && !tcd.isFinal()
                && vt instanceof DerivedTypeDeclarer vdt
                && vdt.def() instanceof ClassDefinition vcd && !vcd.isFinal()
                && isSubclass(vcd, tcd)) {
            write("((").write(tcd.symbol()).write(" *)");
            if (v.unbound()) write('(').write(vt).write("[1]){").write(v).write('}');
            else write('(').write(vt).write("[1]){").write(v).write('}');
            return write(')');
        }
        // class value → interface ref: pass the object's address (param is void*)
        if (t instanceof DerivedTypeDeclarer tdt
                && tdt.def() instanceof InterfaceDefinition) {
            if (v.unbound()) return write('(').write(vt).write("[1]){").write(v).write('}');
            return write('(').write(vt).write("[1]){").write(v).write('}');
        }
        if (t instanceof ArrayTypeDeclarer at && at.refer().has()
                && at.refer().get().isKind(PHANTOM)
                && vt instanceof ArrayTypeDeclarer avt
                && at.element().baseTypeSame(avt.element())) {
            // same-element array → phantom array: wrap in per-type PRef
            write("(Feng$ArrayPRef_").write(typeKey(at.element())).write("){");
            if (avt.refer().none())
                write("(void*)").write(v).write(".$values, ").write(avt.len());
            else
                write(v).write(".$values, ").write(v).write(".$length");
            return write('}');
        }
        // remaining cases (unrelated data types, scalar↔array reinterpret)
        // share the castRef reinterpretation logic
        return castRef(v, t);
    }

    private CGenerator writeLiteral(LiteralExpression v, TypeDeclarer t) {
        if (v.literal() instanceof NilLiteral) return castRef(v, t);
        if (v.literal() instanceof StringLiteral sl) {
            var r = t.maybeRefer();
            if (r.none()) return writeData(sl, t);
            write('(').write("Feng$").write(typeKey(t)).write("){");
            if (r.get().isKind(PHANTOM)) {
                write("(void*)");
            } else {
                write("Feng$inc(");
            }
            literalString(sl).write(".array.$values");
            if (r.get().isKind(PHANTOM))
                write(", ").write(sl.length());
            else
                write("), ").write(sl.length());
            return write('}');
        }
        return write(v);
    }

    private CGenerator baseTypeSymbol(TypeDeclarer td) {
        if (td instanceof PrimitiveTypeDeclarer ptd) return write(ptd.primitive());
        if (td instanceof DerivedTypeDeclarer dtd) return write(dtd.derivedType());
        return unreachable();
    }

    // ---- expression dispatcher ----

    private CGenerator write(Expression e) {
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
            case PairsExpression ee -> unsupported("pairs");
            case null, default -> unreachable();
        };
    }

    private CGenerator write(BinaryExpression e) {
        var op = e.operator();
        var lt = e.left().resultType.must();
        // class operator override → direct call to the macro method
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
            // value type struct/class comparison → memcmp
            write("memcmp(&(").write(e.left()).write("), &(");
            write(e.right()).write("), sizeof(").write(lt).write("))");
            if (op == BinaryOperator.EQ) write(" == 0");
            else write(" != 0");
            return this;
        }
        if (op == BinaryOperator.POW) {
            // Feng ^ operator → C pow() function
            write("pow((").write(e.left()).write("),(");
            write(e.right()).write("))");
            return this;
        }
        var sop = cBinOp(op);
        write('(').write(e.left()).write(')');
        write(' ').write(sop).write(' ');
        write('(').write(e.right()).write(')');
        return this;
    }

    private CGenerator write(UnaryExpression e) {
        var op = e.operator();
        var ot = e.operand().resultType.must();
        // class operator override → direct call to the macro method
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
     * Direct call to an overridden operator macro method. Operands are
     * materialized in a GCC statement expression because the method takes
     * phantom refs (pointers) and operands may be rvalues:
     * <pre>({ struct T _op0 = (l); struct T _op1 = (r); T$feng$macro$operator$add(&_op0, &_op1); })</pre>
     */
    private CGenerator writeOperatorCall(
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
        write(cd.symbol()).write(cm.name()).write('(');
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) write(", ");
            write(args.get(i));
        }
        return write("); })");
    }

    private CGenerator write(LiteralExpression e) {
        return writeLit(e.literal(), e.expectType.get());
    }

    private CGenerator writeLit(Literal lit, Optional<TypeDeclarer> et) {
        if (lit instanceof IntegerLiteral il) return write(il);
        if (lit instanceof NilLiteral nl) {
            if (et.has() && et.get() instanceof ArrayTypeDeclarer)
                return write("{}");
            return write(nl);
        }
        if (lit instanceof StringLiteral sl) return write(sl);
        return write(lit.toString());
    }

    private CGenerator write(VariableExpression e) {
        return varName(e.variable());
    }

    private CGenerator write(SymbolExpression e) {
        if (e.generic().isEmpty()) {
            return write(e.symbol());
        }
        // Resolve generic args through mono context
        var resolved = e.generic().stream().map(this::monoResolve).toList();
        // mangled generic name: $make_Int
        e.symbol().module().use(mp -> write(mp.toString()));
        return write('$').write(e.symbol().name().value())
                .write('_').write(resolved.stream()
                        .map(this::typeKey)
                        .collect(Collectors.joining("_")));
    }

    private CGenerator write(FunctionExpression e) {
        if (e.generic().isEmpty()) {
            return write(e.symbol());
        }
        // Resolve generic args through mono context
        var resolved = e.generic().stream().map(this::monoResolve).toList();
        // mangled generic name: $funcName_TypeArgs
        e.symbol().module().use(mp -> write(mp.toString()));
        return write('$').write(e.symbol().name().value())
                .write('_').write(resolved.stream()
                        .map(this::typeKey)
                        .collect(Collectors.joining("_")));
    }

    static final Map<Identifier, String> ArrayMethods = Map.of(
            ArrayTypeDeclarer.MethodSwap.name(), "FENG$SWAP",
            ArrayTypeDeclarer.MethodMove.name(), "FENG$MOVE"
    );

    private CGenerator write(CallExpression e) {
        if (e.callee() instanceof MethodExpression me) {
            var td = me.subject().resultType.must();
            // array built-in methods swap/move → Header.h macros
            if (td instanceof ArrayTypeDeclarer) {
                var mn = ArrayMethods.get(me.method().name());
                assert mn != null;
                write(mn).write('(').write(me.subject());
                for (var a : e.arguments()) {
                    write(", ").write(a);
                }
                return write(')');
            }
            // value-type subject (e.g. obj(n).run()): materialize a
            // temp in a statement expression so taking '&self' is valid C
            // (even unbound expressions like call results need materialization
            // because C forbids & on rvalues)
            var matSelf = td.maybeRefer().none() && me.subject().unbound();
            if (matSelf) {
                write("({ ").write(td).write(" _self = ");
                write(me.subject());
                write("; ");
            }
            if (td instanceof DerivedTypeDeclarer dtd) {
                var def = dtd.def();

                // method-level generics: always direct call (not in any vtable)
                if (!me.generic().isEmpty()) {
                    var classDt = dtd.derivedType();
                    var resolvedMethodArgs = me.generic().stream()
                            .map(this::monoResolve).toList();
                    // the method may be defined in a (generic) ancestor —
                    // dispatch to the owner's concrete instantiation
                    // (e.g. IntBox.map → generic_3$SealedBox_Int$map_Float)
                    var ownerDt = classDt;
                    if (def instanceof ClassDefinition cd) {
                        var owner = cd;
                        while (!owner.methods().exists(me.method().name())
                                && owner.parent().has()
                                && owner.parent().must() != ClassDefinition.ObjectClass) {
                            owner = owner.parent().must();
                        }
                        if (owner != cd) ownerDt = ancestorDt(cd, classDt, owner);
                    }
                    // direct call name with _typeKey suffix:
                    // generic_2$SealedBox_Int$map_Bool(self, args)
                    writeMangledName(ownerDt).write('$').write(me.method().name())
                            .write('_').write(resolvedMethodArgs.stream()
                                    .map(this::typeKey).collect(Collectors.joining("_")));
                    write('(');
                    // cast to owner struct when dispatching to an ancestor's method
                    if (!ownerDt.equals(classDt))
                        write("(").writeMangledName(ownerDt).write(" *)");
                    if (td.maybeRefer().none()) write('&');
                    if (matSelf) write("_self");
                    else write(me.subject());
                    if (!e.arguments().isEmpty()) {
                        write(", ");
                        writeValues(e.arguments(), e.prototype().must().parameterSet().types());
                    }
                    write(')');
                    if (matSelf) write("; })");
                    return this;
                }

                if (def instanceof InterfaceDefinition iface) {
                    // interface dispatch: find vtable via object's $meta
                    var hasGeneric = !dtd.derivedType().generic().isEmpty();
                    write("((");
                    if (hasGeneric) {
                        write("Feng$Meta_").write(mangledName(dtd.derivedType()));
                    } else {
                        writeMetaType(iface);
                    }
                    write("*)Feng$iface_vtable(");
                    write("*(Feng$Meta**)");
                    write(me.subject());
                    write(",");
                    if (hasGeneric) {
                        write("&Feng$meta_").write(mangledName(dtd.derivedType())).write(".base");
                    } else {
                        write("&Feng$meta_").write(iface.symbol()).write(".base");
                    }
                    write("))->").write(me.method().name());
                } else if (def instanceof ClassDefinition cd && !cd.isFinal()
                        && td.maybeRefer().has()) {
                    // find the class that first defines this method (for correct vtable slot)
                    var master = cd;
                    var mName = me.method().name();
                    while (!master.methods().exists(mName) && master.parent().has()) {
                        master = master.parent().must();
                    }
                    // macro methods (index get/set) are not in any vtable — direct call
                    if (!master.methods().exists(mName)) {
                        var owner = me.method() instanceof ClassMethod cmm
                                && cmm.master() != null ? cmm.master() : cd;
                        write(owner.symbol()).write(mName);
                        write('(').write(me.subject());
                        if (!e.arguments().isEmpty()) {
                            write(", ");
                            writeValues(e.arguments(), e.prototype().must().parameterSet().types());
                        }
                        return write(')');
                    }
                    // use mangled meta type for generic instantiations
                    if (!dtd.derivedType().generic().isEmpty()) {
                        if (cd == master) {
                            write("((Feng$Meta_").write(mangledName(dtd.derivedType()))
                                    .write("*)");
                        } else {
                            var masterDt = ancestorDt(cd, dtd.derivedType(), master);
                            if (masterDt.generic().isEmpty()) {
                                // master is a non-generic parent (e.g. Base in Child<T> : Base)
                                write("((").writeMetaType(master).write(" *)");
                            } else {
                                write("((Feng$Meta_").write(mangledName(masterDt))
                                        .write("*)");
                            }
                        }
                    } else if (currentTypeMap != null && cd.generic().isEmpty() == false) {
                        // in mono context, compute master's concrete metadata type
                        var concreteArgs = new TypeArguments(cd.generic().pos(),
                                cd.generic().params().stream()
                                        .map(tp -> currentTypeMap.getOrDefault(tp, new GenericTypeDeclarer(tp.pos(), new GenericType(tp.pos(), tp))))
                                        .toList());
                        var concreteDt = new DerivedType(cd.symbol().pos(), cd.symbol(), concreteArgs);
                        concreteDt.def(cd);
                        if (cd == master) {
                            write("((Feng$Meta_").write(mangledName(concreteDt))
                                    .write("*)");
                        } else {
                            var masterDt = ancestorDt(cd, concreteDt, master);
                            if (masterDt.generic().isEmpty()) {
                                // master is a non-generic parent (e.g. Base in Child<T> : Base)
                                write("((").writeMetaType(master).write(" *)");
                            } else {
                                write("((Feng$Meta_").write(mangledName(masterDt))
                                        .write("*)");
                            }
                        }
                    } else {
                        if (master == ClassDefinition.ObjectClass) {
                            write("((Feng$Meta*)");
                        } else {
                            write("((").writeMetaType(master).write(" *)");
                        }
                    }
                    write(me.subject());
                    write("->$meta)->").write(mName);
                } else {
                    // direct call — concrete generic methods carry an extra '$'
                    // separator (Box_Int$$get, cf. implConcreteMethod)
                    writeMangledName(dtd.derivedType());
                    if (!dtd.derivedType().generic().isEmpty()) write('$');
                    write(me.method().name());
                }
            } else {
                write(me.method().name());
            }
            write('(');
            // self: take address for value types
            if (td.maybeRefer().none()) write('&');
            if (matSelf) write("_self");
            else write(me.subject());
            if (!e.arguments().isEmpty()) {
                write(", ");
                writeValues(e.arguments(), e.prototype().must().parameterSet().types());
            }
            write(')');
            if (matSelf) write("; })");
            return this;
        }
        write(e.callee()).write('(');
        writeValues(e.arguments(), e.prototype().must().parameterSet().types());
        return write(')');
    }

    private CGenerator writeValues(List<Expression> values, List<TypeDeclarer> dstTypes) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) write(COMMA);
            writeValue(values.get(i), dstTypes.get(i));
        }
        return this;
    }

    private CGenerator visitNew(NewExpression e) {
        return switch (e.type()) {
            case NewDefinedType t -> visitNewDefined(t, e);
            case NewArrayType t -> visitNewArray(t, e);
            case null, default -> unreachable();
        };
    }

    private CGenerator visitNewDefined(NewDefinedType ndt, NewExpression e) {
        var def = findType(ndt.type());
        var nonFinal = def instanceof ClassDefinition cd && !cd.isFinal();
        var isBuiltin = def.builtin();
        e.arg().use(a -> {
            // new(Foo, {id=2}) → block expression: alloc + assign fields
            write("({ ").write(ndt.type()).write(" *_p = (")
                    .write(ndt.type()).write(" *)");
            if (nonFinal) {
                var cd = (ClassDefinition) def;
                write("Feng$newObject(sizeof(").write(ndt.type()).write("), ");
                if (isBuiltin) write("&Feng$meta_").write(cd.symbol());
                else writeMetaBaseRef(cd, (DerivedType) ndt.type());
                write(")");
            } else
                write("Feng$alloc(sizeof(").write(ndt.type()).write("))");
            write("; ");
            if (a instanceof ObjectExpression oe) {
                if (def instanceof ClassDefinition cd2) {
                    for (var f : cd2.allFields().values()) {
                        var val = oe.entries().tryGet(f.name());
                        if (val.has()) {
                            var ft = cd2.generic().isEmpty() ? f.type()
                                    : resolveFromMap(f.type(), buildTypeMap(cd2.generic(), ((DerivedType) ndt.type()).generic()));
                            write("_p->").write(f.name()).write(" = ")
                                    .writeValue(val.get(), ft).endStmt();
                        }
                    }
                } else if (def instanceof StructureDefinition sd) {
                    for (var f : sd.fields()) {
                        var val = oe.entries().tryGet(f.name());
                        if (val.has()) {
                            write("_p->").write(f.name()).write(" = ")
                                    .writeValue(val.get(), f.type()).endStmt();
                        }
                    }
                }
            }
            write("_p; })");
        }, () -> {
            if (nonFinal) {
                var cd = (ClassDefinition) def;
                write("({ ").write(ndt.type()).write(" *_p = (")
                        .write(ndt.type()).write(" *)Feng$newObject(sizeof(")
                        .write(ndt.type()).write("), ");
                if (isBuiltin) write("&Feng$meta_").write(cd.symbol());
                else writeMetaBaseRef(cd, (DerivedType) ndt.type());
                write("); _p; })");
            } else {
                write("((").write(ndt.type()).write(" *)Feng$alloc(sizeof(")
                        .write(ndt.type()).write(")))");
            }
        });
        return this;
    }

    private CGenerator visitNewArray(NewArrayType t, NewExpression e) {
        var elemKey = typeKey(t.element());
        // return array strong ref struct: {$values, $length}
        write("(Feng$ArraySRef_").write(elemKey).write("){");
        write('(').write(t.element()).write(" *)Feng$alloc(");
        write(t.length()).write("*sizeof(").write(t.element()).write(")), ");
        write(t.length()).write('}');
        return this;
    }

    private CGenerator write(ArrayExpression e) {
        // typed compound literal — bare {..} braces are only valid in
        // declaration initializers, not in return / block-expression contexts
        var at = (ArrayTypeDeclarer) e.resultType.must();
        write('(').write(at).write("){{");
        var types = new RepeatList<>(at.element(), e.size());
        writeValues(e.elements(), types);
        return write("}}");
    }

    private CGenerator write(TupleExpression e) {
        var resultType = (TupleTypeDeclarer) e.resultType.must();
        // typed compound literal — bare {..} braces are only valid in
        // declaration initializers, not in return / argument contexts
        write('(').write(resultType).write("){");
        var i = 0;
        for (var elem : e.elements()) {
            if (i > 0) write(", ");
            writeValue(elem, resultType.get(i));
            i++;
        }
        return write('}');
    }

    private CGenerator write(TupleIndexExpression e) {
        write('(').write(e.subject()).write(").v").write(e.index());
        return this;
    }

    private CGenerator write(ObjectExpression oe) {
        var dt = oe.dtd();
        var def = dt.def();
        // classes and structs both use designated initializers
        var cd = def instanceof ClassDefinition c ? c : null;
        var sd = def instanceof StructureDefinition s ? s : null;
        var allFields = cd != null ? cd.allFields().values()
                : sd.fields();
        // Anonymous struct/unions: omit type prefix — C can infer from context
        if (sd != null && sd.anonymous()) {
            write('{');
        } else {
            write('(').write(dt).write(')').write('{');
        }
        // non-final class value type: set $meta for virtual dispatch
        if (cd != null && !cd.isFinal()) {
            write(".$meta = ");
            writeMetaBaseRef(cd, dt.derivedType());
        }
        var data = new ArrayList<Groups.G2<Identifier, Expression>>();
        for (var f : allFields) {
            var o = oe.entries().tryGet(f.name());
            if (o.has()) data.add(Groups.g2(f.name(), o.get()));
        }
        if (cd != null && !cd.isFinal() && !data.isEmpty())
            write(", ");
        joinByComma(data, g -> {
            write('.');
            if (sd != null && sd.cType()) write(g.a().value());
            else write(g.a());
            write('=');
            // Use writeValue to properly convert to field type
            var fieldType = cd != null ? cd.allFields().tryGet(g.a())
                    .map(Field::type)
                    : sd != null ? sd.fields().tryGet(g.a())
                    .map(Field::type)
                      : Optional.<TypeDeclarer>empty();
            if (fieldType.has()) writeValue(g.b(), fieldType.get());
            else write(g.b());
        });
        return write('}');
    }

    private CGenerator fieldInit(
            IdentifierMap<? extends Field> fields,
            ObjectExpression init) {
        joinByComma(fields.values(), f -> {
            var o = init.entries().tryGet(f.name());
            if (o.has()) writeValue(o.get(), f.type());
            else defaultValue(f.type());
        });
        return this;
    }

    private CGenerator write(MemberOfExpression e) {
        var td = e.subject().resultType.must();
        if (td instanceof EnumTypeDeclarer etd) return enumMember(e, etd.def());
        if (td instanceof DerivedTypeDeclarer dtd && dtd.def() instanceof EnumDefinition ed)
            return enumMember(e, ed);
        if (td instanceof ArrayTypeDeclarer) {
            write('(').write(e.resultType.must()).write(')');
        }
        ofMember(e.subject());
        write(e.member());
        return this;
    }

    private CGenerator enumMember(MemberOfExpression e, EnumDefinition ed) {
        if (EnumDefinition.TokenFieldId.equals(e.member().value()))
            return write(e.subject());
        var mid = e.member().value();
        if ("name".equals(mid)) {
            // PRef → SRef: (Feng$ArraySRef_Byte){Feng$inc((void*)data), len}
            write("(Feng$ArraySRef_Byte){Feng$inc((void*)");
            enumName(ed).write('[').write(e.subject()).write("].$name.$values), ");
            enumName(ed).write('[').write(e.subject()).write("].$name.$length}");
            return this;
        }
        enumName(ed).write('[').write(e.subject()).write("].").write(e.member());
        return this;
    }

    private CGenerator ofMember(Expression subject) {
        write(subject);
        var td = subject.resultType.must();
        if (td instanceof ArrayTypeDeclarer || td.maybeRefer().none())
            return write('.');
        return write("->");
    }

    private CGenerator write(IndexOfExpression e) {
        write(e.subject());
        write(".$values[").write(e.index()).write(']');
        return this;
    }

    private CGenerator index(
            PrimaryExpression subject, Expression index) {
        write(subject);
        write(".$values[").write(index).write(']');
        return this;
    }

    private CGenerator write(ConvertExpression e) {
        return write('(').write(e.primitive()).write(")(").write(e.operand()).write(')');
    }

    private CGenerator write(CheckNilExpression e) {
        if (e.nil()) write('!');
        write('(').write(e.subject());
        // array ref structs: nil-check the data pointer
        if (e.subject().resultType.must() instanceof ArrayTypeDeclarer)
            write(".$values");
        write(")");
        return this;
    }

    private CGenerator write(ReferEqualExpression e) {
        write(e.left());
        var lt = e.left().resultType.must();
        if (lt instanceof ArrayTypeDeclarer) write(".$values");
        write(e.same() ? " == " : " != ");
        write(e.right());
        var rt = e.right().resultType.must();
        if (rt instanceof ArrayTypeDeclarer) write(".$values");
        return this;
    }

    private CGenerator write(ConditionalExpression e) {
        write(e.condition()).write(" ? ");
        var rt = e.resultType.must();
        var isRef = rt.maybeRefer().has();
        if (isRef) write('(').write(rt).write(')');
        write(e.yes());
        write(" : ");
        if (isRef) write('(').write(rt).write(')');
        write(e.not());
        return this;
    }

    private CGenerator write(BlockExpression e) {
        write("({").indent();
        for (var s : e.block()) write(s);
        var rt = e.result().resultType.getOrElse(e.resultType);
        writeValue(e.result(), rt.must()).endStmt();
        return dedent().write("})");
    }

    private CGenerator write(ParenExpression e) {
        write('(');
        write(e.child());
        return write(')');
    }

    private CGenerator write(DereferExpression e) {
        return write("(*").write(e.subject()).write(')');
    }

    private CGenerator write(EnumValueExpression e) {
        return write(e.value().id());
    }

    private CGenerator write(EnumIdExpression e) {
        var t = e.index().resultType.must();
        return write("Feng$checkIndex(").write(e.index())
                .write(',').write('(').write(t).write(')')
                .write(e.def().size())
                .write(", (Uint64)(uintptr_t)&&_feng_fn_label, ")
                .write(e.pos().start() != null ? e.pos().start().getLine() : 0)
                .write(')');
    }

    private CGenerator write(IsExpression e) {
        if (!e.needCheck()) {
            // compile-time safe upcast → direct cast
            return castRef(e.subject(), e.type());
        }
        // runtime RTTI check
        var dst = e.type();
        var def = dst.def();
        if (def instanceof InterfaceDefinition iface) {
            // interface check: evaluate subject once via block expression
            var needInc = dst.isKind(STRONG) && !e.unbound();
            write("({ void* _s = (void*)(").write(e.subject()).write("); ");
            if (needInc) write("Feng$inc(");
            write("((Feng$iface_vtable(*(Feng$Meta**)_s,");
            if (!dst.derivedType().generic().isEmpty()) {
                write("&Feng$meta_").write(mangledName(dst.derivedType())).write(".base");
            } else {
                write("&Feng$meta_").write(iface.symbol()).write(".base");
            }
            write(")) ? _s : NULL)");
            if (needInc) write(")");
            write("; })");
        } else {
            // class hierarchy check: evaluate subject once via block expression
            var subjIsIface = e.subject().resultType.match(t ->
                    t instanceof DerivedTypeDeclarer dtd && dtd.def() instanceof InterfaceDefinition);
            var needInc = dst.isKind(STRONG) && !e.unbound();
            write("({ void* _s = (void*)(").write(e.subject()).write("); ");
            if (needInc) write("Feng$inc(");
            write("((Feng$is_kind(");
            if (subjIsIface) write("*(Feng$Meta**)_s");
            else write("(($Object*)_s)->$meta");
            write(",");
            writeMetaBaseRef((ClassDefinition) def, dst.derivedType());
            write(")) ? (").write(dst).write(")_s : NULL)");
            if (needInc) write(")");
            write("; })");
        }
        return this;
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
            case POW -> unsupported("pow");
        };
    }

    // ---- literals ----

    private CGenerator write(IntegerLiteral e) {
        write(e.value().toString(e.radix()));
        return this;
    }

    private CGenerator write(NilLiteral e) {
        write("NULL");
        return this;
    }

    private CGenerator writeData(StringLiteral e, TypeDeclarer t) {
        write('(').write(t).write("){");
        for (byte b : e.value()) write(b).write(',');
        return write('}');
    }

    private CGenerator write(StringLiteral e) {
        return literalString(e);
    }

    private CGenerator literalString(StringLiteral sl) {
        return write("Feng$constString_").write(sl.id());
    }

    // ---- helpers ----

    private Optional<ClassDefinition> findClass(TypeDeclarer td) {
        if (td instanceof DerivedTypeDeclarer dtd) {
            var def = dtd.def();
            if (def instanceof ClassDefinition cd) return Optional.of(cd);
        }
        return Optional.empty();
    }

    /**
     * Count non-builtin parent levels for metadata nesting.
     * Built-in classes (Object, Exception) use plain Feng$Meta without .base wrappers.
     */
    private int parentDepth(ClassDefinition cd) {
        int d = 0;
        var cur = cd;
        while (cur.parent().has() && !cur.parent().must().builtin()) {
            d++;
            cur = cur.parent().must();
        }
        return d;
    }

    /**
     * Write reference to a class's base Feng$Meta (e.g. &Feng$meta_Foo.base or .base.base)
     */
    private CGenerator writeMetaBaseRef(ClassDefinition cd) {
        write("&Feng$meta_").write(cd.symbol());
        if (!cd.builtin()) {
            int d = parentDepth(cd) + 1;
            for (int i = 0; i < d; i++) write(".base");
        }
        return this;
    }

    /**
     * Version for concrete generic instantiations using mangled name.
     */
    private CGenerator writeMetaBaseRef(DerivedType dt) {
        write("&Feng$meta_").write(mangledName(dt));
        if (!((ClassDefinition) dt.def()).builtin()) {
            int d = parentDepth((ClassDefinition) dt.def()) + 1;
            for (int i = 0; i < d; i++) write(".base");
        }
        return this;
    }

    /**
     * Dispatch: use mangled name if generic, else use symbol name.
     */
    private CGenerator writeMetaBaseRef(ClassDefinition cd, DerivedType dt) {
        if (dt.generic().isEmpty()) return writeMetaBaseRef(cd);
        return writeMetaBaseRef(dt);
    }

    /**
     * Write reference to parent class's base Feng$Meta for .super field
     */
    private CGenerator writeSuperRef(ClassDefinition parentCd) {
        if (parentCd.builtin()) {
            // built-in classes use plain Feng$Meta, no wrapper struct
            return write("&Feng$meta_").write(parentCd.symbol());
        }
        write("(const Feng$Meta*)&Feng$meta_").write(parentCd.symbol());
        int d = parentDepth(parentCd) + 1;
        for (int i = 0; i < d; i++) write(".base");
        return this;
    }

    /**
     * Get the ancestor class at the given depth (0 = self, 1 = parent, etc.)
     */
    private ClassDefinition ancestorAt(ClassDefinition cd, int depth) {
        var cur = cd;
        for (int i = 0; i < depth && cur.parent().has(); i++) {
            cur = cur.parent().must();
        }
        return cur;
    }

    /**
     * Collect all interfaces implemented by this class and its ancestors (including composed)
     */
    private LinkedHashSet<InterfaceDefinition> allIfaces(ClassDefinition cd) {
        var set = new LinkedHashSet<InterfaceDefinition>();
        var cur = cd;
        while (cur != null && cur != ClassDefinition.ObjectClass) {
            for (var dt : cur.impl().values()) {
                var iface = (InterfaceDefinition) dt.def();
                addIfacesRecursive(set, iface);
            }
            if (cur.parent().none()) break;
            cur = cur.parent().must();
        }
        return set;
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
            var ancCur = cur;  // capture for lambda
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
     * Interface vtable field/wrapper name key: {@code module$I} for a plain
     * interface (matches the legacy non-generic naming), {@code I_Int} for a
     * concrete generic instantiation (matches concrete metadata naming).
     */
    private String ifaceKey(DerivedType ifaceDt) {
        var id = (InterfaceDefinition) ifaceDt.def();
        if (ifaceDt.generic().isEmpty()) {
            var sb = new StringBuilder();
            id.symbol().module().use(mp -> sb.append(mp));
            sb.append('$').append(id.symbol().name().value());
            return sb.toString();
        }
        return mangledName(ifaceDt);
    }

    /**
     * Plain (non-generic) DerivedType for a class definition.
     */
    private DerivedType plainDt(ClassDefinition cd) {
        var dt = new DerivedType(cd.symbol().pos(), cd.symbol(), TypeArguments.EMPTY);
        dt.def(cd);
        return dt;
    }

    /**
     * Resolve interface type args from an ancestor's param space to the current class's
     * concrete type args. Must be called within {@code withMono(cd.generic(), dt.generic(), ...)}.
     */
    private DerivedType resolveAncestorIface(ClassDefinition cd, ClassDefinition anc, DerivedType ancIface) {
        var clone = ancIface.clone();
        var newArgs = ancIface.generic().stream()
                .map(arg -> resolveArgFromAncestor(cd, anc, arg))
                .toList();
        clone.generic(new TypeArguments(clone.pos(), newArgs));
        return clone;
    }

    /**
     * Resolve a single type arg from an ancestor's param space through the inheritance chain
     * to concrete. Uses the current monomorphization context (currentTypeMap) for cd's type args.
     */
    private TypeDeclarer resolveArgFromAncestor(ClassDefinition cd, ClassDefinition anc, TypeDeclarer arg) {
        // Extract class-level args from currentTypeMap in declaration order
        var cdArgs = cd.generic().isEmpty() || currentTypeMap == null ? null
                : new TypeArguments(cd.generic().pos(),
                cd.generic().stream()
                        .map(tp -> currentTypeMap.getOrDefault(tp, new GenericTypeDeclarer(tp.pos(), new GenericType(tp.pos(), tp))))
                        .toList());
        return resolveArgFromAncestor(cd, anc, arg, cdArgs);
    }

    /**
     * Same as the 3-arg variant but with the concrete type arguments of {@code cd}
     * passed explicitly. This avoids depending on ambient currentTypeMap, which is
     * null when resolving a virtual dispatch outside any monomorphization context
     * (e.g. a non-generic function calling a method on a concrete generic instance).
     */
    private TypeDeclarer resolveArgFromAncestor(ClassDefinition cd, ClassDefinition anc,
                                                TypeDeclarer arg, TypeArguments cdArgs) {
        if (!arg.hasTypeVar()) return arg;
        if (anc == cd) return resolveFromMap(arg, buildTypeMap(cd.generic(), cdArgs));

        // Walk from anc down to cd, applying positional type parameter substitution at each level
        var cur = anc;
        TypeDeclarer result = arg;
        while (cur != cd) {
            // find child that inherits from cur
            var child = findChild(cur, cd);
            if (child == null) break;
            var inheritArgs = child.inherit().must().generic();
            // resolve each GenericTypeDeclarer in result by position in cur's params
            result = resolveFromMap(result, buildTypeMap(cur.generic(), inheritArgs));
            cur = child;
        }

        // Final: resolve cd.params → concrete using positional mapping
        return resolveFromMap(result, buildTypeMap(cd.generic(), cdArgs));
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

    private void addIfacesRecursive(LinkedHashSet<InterfaceDefinition> set, InterfaceDefinition iface) {
        if (!set.add(iface)) return;
        for (var part : iface.partDefs) {
            addIfacesRecursive(set, part);
        }
    }

    /**
     * Check if child is a subclass of ancestor (walk parent chain)
     */
    private boolean isSubclass(ClassDefinition child, ClassDefinition ancestor) {
        var cur = child;
        while (cur.parent().has()) {
            cur = cur.parent().must();
            if (cur == ancestor) return true;
        }
        return false;
    }

    private TypeDefinition findType(DefinedType dt) {
        if (dt instanceof PrimitiveType pt) return pt.primitive().type();
        return ((DerivedType) dt).def();
    }

    /**
     * Write "Feng$Meta_$ClassName"
     */
    private CGenerator writeMetaType(TypeDefinition def) {
        return write("Feng$Meta_").write(def.symbol());
    }

    /**
     * Write a vtable function pointer entry
     */
    private void writeVTableEntry(Method m, TypeDefinition def) {
        var pt = m.prototype();
        pt.returnSet().use(this::write, () -> write("void"));
        write(" (*").write(m.name()).write(")(void* self");
        var ps = pt.parameterSet();
        if (!ps.isEmpty()) {
            write(", ");
            var first = true;
            for (var p : ps) {
                if (first) first = false;
                else write(", ");
                write(((FixedParameter) p).type());
            }
        }
        write(')').endStmt();
    }


    // ===================================================================
    //  Statements — control flow and declarations
    // ===================================================================

    private CGenerator write(Statement e) {
        switch (e) {
            case DeclarationStatement ee -> write(ee);
            case AssignmentsStatement ee -> write(ee);
            case BlockStatement ee -> write(ee);
            case BreakStatement ee -> write(ee);
            case CallStatement ee -> write(ee);
            case ContinueStatement ee -> write(ee);
            case ForStatement ee -> write(ee);
            case IfStatement ee -> write(ee);
            case LabeledStatement ee -> write(ee);
            case ReturnStatement ee -> write(ee);
            case SwitchStatement ee -> write(ee);
            case ThrowStatement ee -> write(ee);
            case TryStatement ee -> write(ee);
            case AssertStatement ee -> write(ee);
            default -> unreachable();
        }
        return this;
    }

    private CGenerator write(List<Statement> list) {
        for (var s : list) write(s);
        return this;
    }

    private CGenerator write(DeclarationStatement ds) {
        ds.variables().forEach(this::declareVar);
        return this;
    }

    private CGenerator write(AssignmentsStatement as) {
        for (var a : as.list()) {
            if (a.replacer().has()) {
                write(a.replacer().must());
                continue;
            }
            writeAssign(a.operand(), a.value()).endStmt();
        }
        return this;
    }

    private CGenerator writeAssign(Operand o, Expression v) {
        var t = o.type.must();
        var r = t.maybeRefer();
        if (r.has()) {
            if (r.get().isKind(PHANTOM)) {
                return write(o).write(" = ").castRef(v, t);
            }
            // strong ref: cleanup old value before assignment
            if (t instanceof ArrayTypeDeclarer atd) {
                // array SRef: use typed temp + array cleanup
                var ek = typeKey(atd.element());
                write(o).write(" = ({ Feng$ArraySRef_").write(ek).write(" _t = ");
                castRef(v, t);
                write("; Feng$cleanup_arr_").write(ek).write("(&");
                write(o);
                write("); _t; })");
            } else {
                // simple pointer: use void* temp + generic cleanup
                write(o).write(" = ({ void* _t = (void*)(");
                castRef(v, t);
                write("); Feng$cleanup_sref(&");
                write(o);
                write("); _t; })");
            }
            return this;
        }
        write(o).write(" = ").write(v);
        return this;
    }

    private CGenerator write(Operand e) {
        switch (e) {
            case IndexOperand ee -> write(ee);
            case TupleOperand ee -> write(ee);
            case FieldOperand ee -> write(ee);
            case VariableOperand ee -> write(ee);
            case DereferOperand ee -> write(ee);
            default -> unreachable();
        }
        return this;
    }

    private CGenerator write(VariableOperand e) {
        varName(e.variable().must());
        return this;
    }

    private CGenerator write(IndexOperand e) {
        return index(e.subject(), e.index());
    }

    private CGenerator write(TupleOperand e) {
        write('(').write(e.subject()).write(").v").write(e.index());
        return this;
    }

    private CGenerator write(FieldOperand e) {
        ofMember(e.subject());
        write(e.field());
        return this;
    }

    private CGenerator write(CurrentExpression e) {
        return write("_self");
    }

    private CGenerator write(MethodExpression e) {
        ofMember(e.subject());
        write(e.method().name());
        if (!e.generic().isEmpty()) {
            write('_').write(e.generic().stream()
                    .map(this::typeKey)
                    .collect(Collectors.joining("_")));
        }
        return this;
    }

    private CGenerator derefer(PrimaryExpression e) {
        return write("(*").write(e).write(')');
    }

    private CGenerator write(DereferOperand e) {
        return derefer(e.subject());
    }

    private CGenerator write(BlockStatement bs) {
        if (bs.newScope()) write('{').indent();
        write(bs.list());
        if (bs.newScope()) {
            if (noTerminal(bs.list())) exitScope(bs);
            dedent().write('}').newLine();
        }
        return this;
    }

    private CGenerator write(BreakStatement s) {
        return write("break").endStmt();
    }

    private CGenerator write(ContinueStatement s) {
        return write("continue").endStmt();
    }

    private CGenerator write(CallStatement e) {
        if (e.replace().has()) {
            write(e.replace().must());
        } else {
            write((Expression) e.call()).endStmt();
        }
        return this;
    }

    private CGenerator write(ForStatement e) {
        return switch (e) {
            case ConditionalForStatement ee -> write(ee);
            case IterableForStatement ee -> write(ee.replace.must());
            case null, default -> unreachable();
        };
    }

    private CGenerator write(ConditionalForStatement fs) {
        write('{').indent();
        fs.initializer().use(this::write);
        write("for(;;) {").indent();
        write("if(").write(fs.condition()).write("){").indent();
        write(fs.body());
        dedent().write("}else{").indent();
        write("break").endStmt();
        dedent().write('}').newLine();
        fs.updater().use(this::write);
        dedent().write('}').newLine();
        dedent().write('}').newLine();
        return this;
    }

    private CGenerator write(IfStatement is) {
        is.init().use(s -> {
            write('{').indent();
            write(s);
        });
        write("if(").write(is.condition()).write(')');
        write(is.yes());
        is.not().use(s -> write(" else ").write(s));
        if (is.init().has()) {
            dedent().write('}').newLine();
        }
        return this;
    }

    private CGenerator write(LabeledStatement s) {
        return write(s.label()).write(':').write(s.target());
    }

    private CGenerator write(ReturnStatement rs) {
        // Inside try-with-finally: defer return until after finally executes
        if (insideTryFinally) {
            int depth = tryFinallyDepth - 1; // depth was already incremented for current try
            if (rs.result().none()) {
                write("_feng_returned").write(depth).write(" = true; ");
                write("goto _feng_finally_").write(depth).endStmt();
            } else {
                var re = rs.result().get();
                write("_feng_retval").write(depth).write(" = ").write(re).endStmt();
                write("_feng_returned").write(depth).write(" = true; ");
                write("goto _feng_finally_").write(depth).endStmt();
            }
            return this;
        }
        if (rs.result().none()) return write("return").endStmt();
        var re = rs.result().get();
        var prot = rs.procedure().must().prototype();
        var rt = prot.returnSet().must();
        return write("return ").writeValue(re, rt).endStmt();
    }

    private CGenerator write(ThrowStatement ts) {
        var exExpr = ts.exception();
        var td = (DerivedTypeDeclarer) exExpr.resultType.must();
        var cd = (ClassDefinition) td.def();

        // Find the trace method (defined on Exception or a subclass)
        var traceMethod = findTraceMethod(cd);

        // ({ void* _ex = <expr>; Class$trace(_ex, fn, ln); Feng$throw(_ex); })
        write("({ void* _ex = (void*)(");
        write(exExpr);
        write("); ");
        if (traceMethod != null) {
            var owner = traceMethod.master() != null
                    ? (ClassDefinition) traceMethod.master() : cd;
            write(owner.symbol());
            write("$trace(_ex, ");
        } else {
            write("Feng$errorSetTrace(_ex, ");
        }
        write("(Uint64)(uintptr_t)&&_feng_fn_label, ");
        write(ts.pos().start() != null ? ts.pos().start().getLine() : 0);
        write("); ");
        write("Feng$throw(_ex); __builtin_unreachable(); })");
        return endStmt();
    }

    /**
     * Find the trace method on a class or its ancestors.
     * Returns the method from the class that actually DEFINES it (not inherited copies).
     */
    private ClassMethod findTraceMethod(ClassDefinition cd) {
        var traceId = new Identifier("trace");
        // check own methods first (avoid inherited copies with wrong master)
        var m = cd.methods().tryGet(traceId);
        if (m.has()) return (ClassMethod) m.get();
        // walk parent chain
        for (var p = cd.parent(); p.has(); p = p.get().get().parent()) {
            m = p.get().get().methods().tryGet(traceId);
            if (m.has()) return (ClassMethod) m.get();
        }
        return null;
    }

    private CGenerator write(AssertStatement as) {
        if (!debug) return this;  // no-op in non-debug mode

        write("if (!(");
        write(as.condition());
        write(")) { ");
        // ({ $AssertException* _ex = Feng$alloc(sizeof($AssertException));
        //    _ex->$meta = &Feng$meta_$AssertException;
        //    $Exception$trace(_ex, (Uint64)(uintptr_t)&&_feng_fn_label, line);
        //    Feng$throw(_ex); __builtin_unreachable(); })
        write("({ $AssertException* _ex = Feng$alloc(sizeof($AssertException)); ");
        write("_ex->$meta = &Feng$meta_$AssertException; ");
        write("$Exception$trace(_ex, ");
        write("(Uint64)(uintptr_t)&&_feng_fn_label, ");
        write(as.pos().start() != null ? as.pos().start().getLine() : 0);
        write("); ");
        write("Feng$throw(_ex); __builtin_unreachable(); })");
        endStmt();
        write(" }");
        return this;
    }

    private CGenerator write(TryStatement ts) {
        boolean hasFinally = ts.finallyClause().has();
        boolean hasCatches = !ts.catchClauses().isEmpty();
        int depth = tryFinallyDepth;

        write('{').indent();

        // Return-tracking: needed only when finally exists (return must defer to after finally)
        if (hasFinally) {
            var proc = procOf(ts.body());
            if (proc != null && proc.returnSet().has()) {
                write("volatile ");
                write(proc.returnSet().must());
                write(" _feng_retval").write(depth).write("; ");
            }
            write("volatile bool _feng_returned").write(depth).write(" = false; ").newLine();
        }

        // Exception frame (volatile: values must survive longjmp per C11)
        write("volatile Feng$ExFrame _frame").write(depth);
        write(" = {.prev = Feng$ex_top}; ");
        write("Feng$ex_top = (Feng$ExFrame*)&_frame").write(depth).endStmt();

        write("if (setjmp(*(jmp_buf*)&_frame").write(depth).write(".buf) == 0) {").indent();

        // === TRY body ===
        if (hasFinally) {
            insideTryFinally = true;
            tryFinallyDepth++;
        }
        write(ts.body());
        if (hasFinally) {
            tryFinallyDepth--;
            if (tryFinallyDepth == 0) insideTryFinally = false;
        }
        dedent();

        if (hasCatches) {
            write("} else {").indent();
            write("void* _ex = _frame").write(depth).write(".exception;").newLine();

            boolean first = true;
            for (var cc : ts.catchClauses()) {
                if (first) {
                    write("if (");
                } else {
                    write(" else if (");
                }
                first = false;

                boolean firstType = true;
                for (var catchType : cc.typeSet()) {
                    if (!firstType) write(" || ");
                    firstType = false;
                    if (catchType instanceof DerivedTypeDeclarer dtd
                            && dtd.def() instanceof ClassDefinition ccd) {
                        // class → use is_kind (supports parent class matching)
                        write("Feng$is_kind(Feng$objMeta(_ex), (const Feng$Meta*)&Feng$meta_");
                        write(ccd.symbol());
                        write(")");
                    } else if (catchType instanceof DerivedTypeDeclarer dtd
                            && dtd.def() instanceof InterfaceDefinition ifd) {
                        // interface → use iface_vtable (supports interface matching)
                        write("Feng$iface_vtable(Feng$objMeta(_ex), (const Feng$Meta*)&Feng$meta_");
                        write(ifd.symbol());
                        write(") != NULL");
                    } else {
                        unreachable();
                    }
                }
                write(") {").indent();

                // Declare catch variable; if single type, use typed pointer
                var arg = cc.argument();
                if (cc.typeSet().size() == 1) {
                    var ctd = (DerivedTypeDeclarer) cc.typeSet().get(0);
                    var def = ctd.def();
                    if (def instanceof InterfaceDefinition) {
                        // interface → void* (no concrete C struct for interface)
                        write("void* ").varName(arg).write(" = _ex;").newLine();
                    } else {
                        var ccd = (ClassDefinition) def;
                        write(ccd.symbol()).write("* ").varName(arg)
                                .write(" = (").write(ccd.symbol()).write("*)_ex;").newLine();
                    }
                } else {
                    write("void* ").varName(arg).write(" = _ex;").newLine();
                }

                write(cc.body());
                write("Feng$dec(_ex); ");  // release ref held by frame
                write("_frame").write(depth).write(".state = 1; /* caught */").newLine();
                dedent().write('}');
            }
            write(" { /* fallthrough */ }").newLine(); // all catches unmatched
            dedent();
        }
        write('}').newLine();

        write("Feng$ex_top = _frame").write(depth).write(".prev;").newLine();

        // === FINALLY ===
        if (hasFinally) {
            write("_feng_finally_").write(depth).write(':').newLine();
            write(ts.finallyClause().must());
            write("if (_feng_returned").write(depth).write(") return _feng_retval")
                    .write(depth).endStmt();
        }

        // Re-throw unhandled exception
        if (hasCatches) {
            write("if (_frame").write(depth)
                    .write(".state != 1 && _frame").write(depth)
                    .write(".exception) { Feng$throw(_frame").write(depth)
                    .write(".exception); }").newLine();
        } else if (hasFinally) {
            write("if (_frame").write(depth)
                    .write(".exception) { Feng$throw(_frame").write(depth)
                    .write(".exception); }").newLine();
        }

        dedent().write('}').newLine();
        return this;
    }

    /**
     * Search a statement tree for a ReturnStatement to extract the enclosing
     * Procedure's prototype (for return type in try-finally).
     */
    private Prototype procOf(Statement s) {
        if (s instanceof ReturnStatement rs && rs.procedure().has()) {
            return rs.procedure().must().prototype();
        }
        if (s instanceof BlockStatement bs) {
            for (var st : bs.list()) {
                var p = procOf(st);
                if (p != null) return p;
            }
        }
        if (s instanceof IfStatement is) {
            var p = procOf(is.yes());
            if (p != null) return p;
            if (is.not().has()) {
                p = procOf(is.not().get());
                if (p != null) return p;
            }
        }
        if (s instanceof ForStatement fs) {
            if (fs instanceof ConditionalForStatement cfs) {
                return procOf(cfs.body());
            }
        }
        if (s instanceof SwitchStatement ss) {
            for (var br : ss.branches()) {
                var p = procOf(br.body());
                if (p != null) return p;
            }
        }
        if (s instanceof TryStatement ts) {
            var p = procOf(ts.body());
            if (p != null) return p;
        }
        return null;
    }

    private CGenerator write(SwitchStatement ss) {
        if (ss.init().has()) {
            write('{');
            write(ss.init().get());
        }
        write("switch(").write(ss.value()).write("){");
        for (var br : ss.branches()) {
            for (var cs : br.constants()) write("case ").write(cs).write(':');
            write(br);
            write("break;").newLine();
        }
        ss.defaultBranch().use(br -> {
            write("default: ");
            write(br);
        });
        write('}').newLine();
        if (ss.init().has()) write('}').newLine();
        return this;
    }

    private CGenerator write(Branch e) {
        write((Statement) e.body());
        return this;
    }

    private boolean noTerminal(List<Statement> list) {
        if (list.isEmpty()) return false;
        var last = list.getLast();
        return !(last instanceof ReturnStatement
                || last instanceof ThrowStatement);
    }

    private void exitScope(Scope s) {
        // RAII via __attribute__((cleanup)) handles cleanup automatically
    }

    // ===================================================================
    //  Top-level structures — struct, enum, class, function, globals
    // ===================================================================

    // ---- forward declarations ----

    private void declareType() {
        if (table.module.has() && !header) return;
        writeComment("type declarations");
        for (var t : table.enumList) declareType(t);
        for (var t : table.dagStructures) declareType(t);
        for (var t : table.dagInterfaces) declareType(t);
        for (var t : table.dagClasses) declareType(t);
        newLine();
    }

    void declareType(StructureDefinition def) {
        if (def.cType()) return; // C-imported struct: handled by bridge header
        write("typedef ").write(def.domain().name).write(' ').write(def.symbol())
                .write(' ').write(def.symbol()).endStmt();
    }

    void declareType(ClassDefinition def) {
        if (def.isFinal()) return;
        write("typedef struct ").write(def.symbol()).write(' ').write(def.symbol()).endStmt();
    }

    void declareType(InterfaceDefinition def) {
        if (!def.generic().isEmpty()) return; // generic: concrete only
        write("typedef struct Feng$Meta_").write(def.symbol())
                .write(" Feng$Meta_").write(def.symbol()).endStmt();
    }

    void declareType(EnumDefinition def) {
    }

    /**
     * Forward-declare struct tags for all concrete generic class instantiations
     * (final + non-final) at file scope, so function prototypes that reference them
     * bind to the file-scope tag rather than getting prototype-local scope.
     */
    private void declareConcreteStructForwards() {
        boolean any = false;
        // Forward-declare generic class struct types from concreteTypeInsts.
        // Also check the deprecated concreteInstantiations for class types
        // that aren't yet in concreteTypeInsts.
        var classInsts = new LinkedHashSet<DerivedType>();
        for (var dt : table.concreteInstantiations) {
            if (dt.def() instanceof ClassDefinition && !dt.generic().isEmpty()) {
                classInsts.add(dt);
            }
        }
        for (var dt : classInsts) {
            var mName = mangledName(dt);
            if (emittedTypedefs.add(mName)) {
                write("typedef struct ").write(mName).write(' ').write(mName).endStmt();
                any = true;
            }
        }
        for (var cti : table.concreteTypeInsts) {
            if (cti.def() instanceof ClassDefinition cd && !cd.generic().isEmpty()) {
                var resolvedArgs = new ArrayList<String>();
                for (var tp : cd.generic()) {
                    var resolved = cti.typeMap().get(tp);
                    if (resolved != null) resolvedArgs.add(typeKey(resolved));
                }
                var sb = new StringBuilder();
                cd.symbol().module().use(m -> sb.append(m).append('$'));
                sb.append(cd.symbol().name().value()).append('_')
                        .append(String.join("_", resolvedArgs));
                var mName = sb.toString();
                if (emittedTypedefs.add(mName)) {
                    write("typedef struct ").write(mName).write(' ').write(mName).endStmt();
                    any = true;
                }
            }
        }
        // Forward-declare non-generic class struct types from dagClasses.
        // These must be visible before any typedef that references them
        // (e.g., FixedArray typedef with class element like Feng$Array_A_2).
        for (var cd : table.dagClasses) {
            if (cd.generic().isEmpty()) {
                var sb = new StringBuilder();
                cd.symbol().module().use(m -> sb.append(m).append('$'));
                sb.append(cd.symbol().name().value());
                var mName = sb.toString();
                if (emittedTypedefs.add(mName)) {
                    write("typedef struct ").write(mName).write(' ').write(mName).endStmt();
                    any = true;
                }
            }
        }
        // Forward-declare structure types from dagStructures.
        // Structure types embedded by value in FixedArray/Tuple need
        // the complete struct definition, which comes from
        // structureDefinition(). Forward-declaring here ensures the tag
        // is visible when FixedArray/Tuple typedefs reference it.
        for (var sd : table.dagStructures) {
            if (sd.cType()) continue; // C-imported struct: handled by bridge header
            var sb = new StringBuilder();
            sd.symbol().module().use(m -> sb.append(m).append('$'));
            sb.append(sd.symbol().name().value());
            var mName = sb.toString();
            if (emittedTypedefs.add(mName)) {
                write("typedef struct ").write(mName).write(' ').write(mName).endStmt();
                any = true;
            }
        }
        if (any) newLine();
    }

    // ---- final class (Phase 3a) ----

    private volatile ClassDefinition enterClass;

    /**
     * Emit final-class struct bodies (in header only for multi-file modules).
     */
    private void classesDefinition() {
        writeComment("class/interface definition");
        // Emit concrete generic class struct typedefs FIRST so that
        // interface meta structs (which reference class types like Result_Int
        // in vtable entries) can use them.
        table.dagClasses.bfs(this::declareClass);
        // Now emit interface meta structs (they reference class types in vtable entries)
        table.dagInterfaces.bfs(this::declareInterface);
        // concrete instantiations of IMPORTED generic classes/interfaces —
        // monomorphized locally, the defining module doesn't know about them
        // (e.g. libs_1 using std$map: Node_Int_Int, Map_Int_Int, Result_Int)
        if (!(table.module.has() && !header)) {
            var localIfaces = new HashSet<InterfaceDefinition>();
            for (var id : table.dagInterfaces) localIfaces.add(id);
            var localClasses = new HashSet<ClassDefinition>();
            for (var cd : table.dagClasses) localClasses.add(cd);
            // imported class instantiations first (struct typedefs)
            for (var dt : table.concreteInstantiations) {
                if (dt.def() instanceof ClassDefinition cd
                        && !localClasses.contains(cd) && !cd.generic().isEmpty()) {
                    if (cd.isFinal()) declareConcreteFinalClass(cd, dt);
                    else declareConcreteNonFinalClass(cd, dt);
                }
            }
            // then imported interface instantiations (meta structs reference class types)
            for (var dt : table.concreteInstantiations) {
                if (dt.def() instanceof InterfaceDefinition id
                        && !localIfaces.contains(id) && !id.generic().isEmpty()) {
                    declareConcreteInterface(id, dt);
                }
            }
        }
        newLine();
    }

    /**
     * Emit metadata static instances and vtable wrappers (source file only).
     */
    private void metaDefinitions() {
        if (header) return;
        var hasOop = table.dagClasses.stream().anyMatch(cd -> !cd.isFinal())
                || !table.dagInterfaces.isEmpty()
                // imported generic instantiations need local metadata too
                || !table.concreteInstantiations.isEmpty();
        if (!hasOop) return;

        // Object metadata is declared extern in Header.h, defined in Feng$Builtins.c
        newLine();

        // interface metadata (just base fields)
        writeComment("interface metadata");
        for (var id : table.dagInterfaces) {
            if (id.builtin()) continue; // in Header.h extern + Feng$Builtins.c
            if (!id.generic().isEmpty()) continue; // generic: concrete only
            write("const Feng$Meta_").write(id.symbol())
                    .write(" Feng$meta_").write(id.symbol()).write(" = {").indent();
            write(".base = {").indent();
            write(".instance_size = 0,").newLine();
            write(".super = NULL,").newLine();
            write(".iface_count = 0,").newLine();
            write(".ifaces = NULL,").newLine();
            write(".destroy = NULL").newLine();
            dedent().write('}').newLine();
            dedent().write("}").endStmt();
        }
        // concrete generic interface metadata
        for (var dt : table.concreteInstantiations) {
            if (!(dt.def() instanceof InterfaceDefinition id)) continue;
            withMono(id.generic(), dt.generic(), () -> {
                var mName = mangledName(dt);
                write("const Feng$Meta_").write(mName)
                        .write(" Feng$meta_").write(mName).write(" = {").indent();
                write(".base = {").indent();
                write(".instance_size = 0,").newLine();
                write(".super = NULL,").newLine();
                write(".iface_count = 0,").newLine();
                write(".ifaces = NULL,").newLine();
                write(".destroy = NULL").newLine();
                dedent().write('}').newLine();
                dedent().write("}").endStmt();
            });
        }
        newLine();

        // vtable wrappers for class methods AND interface methods
        writeComment("method forward declarations");
        for (var cd : table.dagClasses) {
            if (cd.isFinal() || !cd.generic().isEmpty()) continue;
            for (var cm : cd.methods()) {
                // forward declare the real method
                var pt = cm.prototype();
                pt.returnSet().use(this::write, () -> write("void"));
                write(' ').write(cd.symbol()).write(cm.name())
                        .write("(void *self");
                if (!pt.parameterSet().isEmpty()) {
                    write(", ").write(pt.parameterSet());
                }
                write(')').endStmt();
            }
        }
        // concrete generic non-final class method forward declarations
        for (var dt : table.concreteInstantiations) {
            var def = dt.def();
            if (!(def instanceof ClassDefinition cd) || cd.isFinal()) continue;
            withMono(cd.generic(), dt.generic(), () -> {
                var mName = mangledName(dt);
                for (var cm : cd.methods()) {
                    if (!cm.generic().isEmpty()) continue; // skip method-level generics
                    var pt = cm.prototype();
                    pt.returnSet().use(this::write, () -> write("void"));
                    write(" ").writeMangledName(dt).write('$').write(cm.name())
                            .write("(void *self");
                    if (!pt.parameterSet().isEmpty()) {
                        write(", ").write(pt.parameterSet());
                    }
                    write(')').endStmt();
                }
            });
        }
        newLine();

        // concrete generic metadata forward declarations —
        // must precede non-generic class metadata: a non-generic class
        // inheriting a generic instantiation references both
        emitConcreteWrappers();
        for (var dt : table.concreteInstantiations) {
            if (!(dt.def() instanceof ClassDefinition ccd) || ccd.isFinal()) continue;
            var mName = mangledName(dt);
            write("const Feng$Meta_").write(mName)
                    .write(" Feng$meta_").write(mName).endStmt();
        }
        newLine();

        // class metadata (with vtable + interface vtables)
        writeComment("class metadata");
        for (var cd : table.dagClasses) {
            if (cd.isFinal() || !cd.generic().isEmpty()) continue;

            // iface entries array
            var cdIfaces = allConcreteIfaces(cd);
            if (!cdIfaces.isEmpty()) {
                write("static const Feng$IfaceEntry Feng$meta_")
                        .write(cd.symbol()).write("_ifaces[] = {").indent();
                for (var ifaceDt : cdIfaces.values()) {
                    var iName = ifaceKey(ifaceDt);
                    write("{ (const Feng$Meta*)&Feng$meta_")
                            .write(iName).write(", offsetof(Feng$Meta_")
                            .write(cd.symbol()).write(", i").write(iName)
                            .write(") },").newLine();
                }
                write("{ NULL, 0 }").newLine();
                dedent().write("}").endStmt();
                newLine();
            }

            // metadata instance
            write("const Feng$Meta_").write(cd.symbol())
                    .write(" Feng$meta_").write(cd.symbol()).write(" = {").indent();

            boolean parentIsObject = cd.parent().none()
                    || cd.parent().must() == ClassDefinition.ObjectClass;

            if (parentIsObject) {
                // Feng$Meta_A = { Feng$Meta base; methods; ifaces; }
                write(".base = {").indent();
                writeBaseMeta(cd);
                dedent().write("},").newLine();
                emitClassMethods(cd, false);
                emitIfaceVTables(cd);
            } else {
                // Multi-level: generate .base for each non-Object parent
                int depth = parentDepth(cd);
                for (int i = 0; i <= depth; i++) {
                    write(".base = {").indent();
                }
                writeBaseMeta(cd);
                for (int i = depth; i >= 0; i--) {
                    dedent().write("},").newLine();
                    if (i > 0) {
                        // parent-level methods: use override if available, else parent's default
                        var anc = ancestorAt(cd, i);
                        for (var am : anc.methods()) {
                            if (!am.generic().isEmpty()) continue; // method-level generics
                            write(".").write(am.name()).write(" = ");
                            if (cd.methods().exists(am.name())) {
                                writeMethodRef(plainDt(cd), am.name());
                            } else {
                                var ancDt = ancestorDt(cd, plainDt(cd), anc);
                                writeMethodRef(ancDt, am.name());
                            }
                            write(',').newLine();
                        }
                    }
                }
                emitNewMethods(cd);
                emitIfaceVTables(cd);
            }

            dedent().write("}").endStmt();
            newLine();
        }

        // concrete generic non-final class metadata
        // (wrappers already emitted before the non-generic class metadata above)
        emitConcreteMetas();
    }

    /**
     * Generate metadata static instances for concrete generic non-final class instantiations.
     */
    private void emitConcreteMetas() {
        for (var dt : table.concreteInstantiations) {
            var def = dt.def();
            if (!(def instanceof ClassDefinition cd) || cd.isFinal()) continue;
            withMono(cd.generic(), dt.generic(), () -> {
                var mName = mangledName(dt);  // e.g., "Box_Int"
                var ifaces = allConcreteIfaces(cd);  // all interfaces (direct + inherited) with concrete args

                // iface entries array (if any)
                if (!ifaces.isEmpty()) {
                    write("static const Feng$IfaceEntry Feng$meta_")
                            .write(mName).write("_ifaces[] = {").indent();
                    for (var ifaceDt : ifaces.values()) {
                        var iName = ifaceKey(ifaceDt);
                        write("{ (const Feng$Meta*)&Feng$meta_").write(iName)
                                .write(", offsetof(Feng$Meta_").write(mName)
                                .write(", i").write(iName).write(") },").newLine();
                    }
                    write("{ NULL, 0 }").newLine();
                    dedent().write("}").endStmt();
                    newLine();
                }

                writeComment("metadata " + mName);
                write("const Feng$Meta_").write(mName)
                        .write(" Feng$meta_").write(mName).write(" = {").indent();
                // handle multi-level inheritance: for B:A, Feng$Meta_B.base is Feng$Meta_A,
                // so we need .base.base.instance_size, not .base.instance_size
                boolean parentIsObject = cd.parent().none()
                        || cd.parent().must() == ClassDefinition.ObjectClass;
                int depth = parentDepth(cd);
                if (parentIsObject) {
                    write(".base = {").indent();
                } else {
                    for (int i = 0; i <= depth; i++) {
                        write(".base = {").indent();
                    }
                }
                write(".instance_size = sizeof(").writeMangledName(dt).write("),").newLine();
                cd.parent().use(p -> {
                    if (p == ClassDefinition.ObjectClass)
                        write(".super = &Feng$meta_$Object,").newLine();
                    else
                        write(".super = (const Feng$Meta*)&Feng$meta_").write(parentMangledName(cd, dt))
                                .write(".base,").newLine();
                }, () -> write(".super = NULL,").newLine());
                if (ifaces.isEmpty()) {
                    write(".iface_count = 0,").newLine();
                    write(".ifaces = NULL,").newLine();
                } else {
                    write(".iface_count = ").write(ifaces.size()).write(',').newLine();
                    write(".ifaces = Feng$meta_").write(mName).write("_ifaces,").newLine();
                }
                write(".destroy = NULL").newLine();
                if (parentIsObject) {
                    dedent().write("},").newLine();
                    // method pointers
                    for (var cm : cd.methods()) {
                        if (!cm.generic().isEmpty()) continue;
                        write(".").write(cm.name()).write(" = ");
                        writeMethodRef(dt, cm.name());
                        write(',').newLine();
                    }
                } else {
                    // multi-level: generate ancestor method slots
                    for (int i = depth; i >= 0; i--) {
                        dedent().write("},").newLine();
                        if (i > 0) {
                            var anc = ancestorAt(cd, i);
                            for (var am : anc.methods()) {
                                write(".").write(am.name()).write(" = ");
                                if (cd.methods().exists(am.name())) {
                                    writeMethodRef(dt, am.name());
                                } else {
                                    writeMethodRef(ancestorDt(cd, dt, anc), am.name());
                                }
                                write(',').newLine();
                            }
                        }
                    }
                    // new methods not inherited from any ancestor
                    for (var cm : cd.methods()) {
                        if (!cm.generic().isEmpty()) continue;
                        boolean inherited = false;
                        var cur = cd;
                        while (cur.parent().has() && cur.parent().must() != ClassDefinition.ObjectClass) {
                            cur = cur.parent().must();
                            if (cur.methods().exists(cm.name())) {
                                inherited = true;
                                break;
                            }
                        }
                        if (!inherited) {
                            write(".").write(cm.name()).write(" = ");
                            writeMethodRef(dt, cm.name());
                            write(',').newLine();
                        }
                    }
                }
                // interface vtables
                for (var ifaceDt : ifaces.values()) {
                    var iName = ifaceKey(ifaceDt);
                    var ifaceDef = (InterfaceDefinition) ifaceDt.def();
                    write(".i").write(iName).write(" = {").indent();
                    write(".base = {},").newLine();
                    for (var im : ifaceDef.allMethods().values()) {
                        write(".").write(im.name()).write(" = ");
                        writeIfaceMethodRef(cd, dt, im);
                        write(',').newLine();
                    }
                    dedent().write("},").newLine();
                }
                dedent().write("}").endStmt();
                newLine();
            });
        }
    }

    /**
     * No-op — vtable wrappers are no longer needed since all methods use void* self.
     */
    private void emitConcreteWrappers() {
    }


    private void writeBaseMeta(ClassDefinition cd) {
        write(".instance_size = sizeof(").write(cd.symbol()).write("),").newLine();
        cd.parent().use(p -> {
            if (p == ClassDefinition.ObjectClass)
                write(".super = &Feng$meta_$Object,");
            else if (cd.inherit().has() && !cd.inherit().must().generic().isEmpty()) {
                write(".super = (const Feng$Meta*)&Feng$meta_").write(mangledName(cd.inherit().must())).write(".base,");
            } else {
                write(".super = ");
                writeSuperRef(p);
                write(',');
            }
        }, () -> write(".super = NULL,"));
        newLine();
        if (allIfaces(cd).isEmpty()) {
            write(".iface_count = 0,").newLine();
            write(".ifaces = NULL,").newLine();
        } else {
            write(".iface_count = ").write(allIfaces(cd).size()).write(',').newLine();
            write(".ifaces = Feng$meta_").write(cd.symbol()).write("_ifaces,").newLine();
        }
        if (cd.resource()) {
            write(".destroy = ").write(cd.symbol())
                    .write(cd.resourceFree().must().name()).write(',').newLine();
        } else {
            write(".destroy = NULL,").newLine();
        }
    }

    /**
     * Emit all method slots (both new and overrides)
     */
    private void emitClassMethods(ClassDefinition cd, boolean inParent) {
        for (var cm : cd.methods()) {
            if (!cm.generic().isEmpty()) continue; // method-level generics: not in vtable
            write(".").write(cm.name()).write(" = ");
            writeMethodRef(plainDt(cd), cm.name());
            write(',').newLine();
        }
    }

    /**
     * Emit only new methods (not inherited from any ancestor)
     */
    private void emitNewMethods(ClassDefinition cd) {
        for (var cm : cd.methods()) {
            if (!cm.generic().isEmpty()) continue; // method-level generics: not in vtable
            var inherited = cd.parent().match(
                    p -> p.allMethods().exists(cm.name()));
            if (!inherited) {
                write(".").write(cm.name()).write(" = ");
                writeMethodRef(plainDt(cd), cm.name());
                write(',').newLine();
            }
        }
    }

    private void emitIfaceVTables(ClassDefinition cd) {
        for (var ifaceDt : allConcreteIfaces(cd).values()) {
            var iface = (InterfaceDefinition) ifaceDt.def();
            var iName = ifaceKey(ifaceDt);
            write(".i").write(iName).write(" = {").indent();
            write(".base = {},").newLine();
            for (var im : iface.allMethods().values()) {
                // Cast method pointer to match interface vtable entry type.
                // The method implementation returns ConcreteType* but the
                // interface vtable entry declares void* (since interface refs
                // are void* in C). C requires explicit cast for function pointers.
                // Use withMono to resolve interface type params to concrete args
                // (e.g. I`O` → I`Int`: O → Int)
                write(".").write(im.name()).write(" = (");
                withMono(iface.generic(), ifaceDt.generic(), () -> {
                    im.prototype().returnSet().use(t -> {
                        var rt = monoResolve(t);
                        if (rt.maybeRefer().has()) write("void*");
                        else write(rt);
                    }, () -> write("void"));
                    write("(*)(void*");
                    var ps = im.prototype().parameterSet();
                    if (!ps.isEmpty()) {
                        for (var p : ps) {
                            write(", ");
                            write(monoResolve(((FixedParameter) p).type()));
                        }
                    }
                    write(')');
                });
                write(')');
                writeIfaceMethodRef(cd, plainDt(cd), im);
                write(',').newLine();
            }
            dedent().write("},").newLine();
        }
    }


    /**
     * Emit final-class method bodies as global functions.
     */
    private void classMethods() {
        writeComment("class method");
        table.dagClasses.bfs(this::implClass);
    }

    void declareInterface(InterfaceDefinition id) {
        if (table.module.has() && !header) return;
        if (id.builtin()) return; // already in builtin.h
        if (!id.generic().isEmpty()) {
            // generate concrete interface metadata types for each instantiation
            for (var dt : table.concreteInstantiations) {
                if (dt.def() == id) {
                    declareConcreteInterface(id, dt);
                }
            }
            return;
        }
        // Ensure struct typedefs for types referenced in vtable entries
        // are emitted before the interface meta struct.
        for (var im : id.allMethods().values()) {
            var pt = im.prototype();
            pt.returnSet().use(this::ensureFieldTypeDecls);
            for (var t : pt.parameterSet().types()) ensureFieldTypeDecls(t);
        }
        var guard = guardName("FENG_STRUCT_Feng$Meta_" + id.symbol());
        write("#ifndef ").write(guard).newLine();
        write("#define ").write(guard).newLine();
        write("typedef struct Feng$Meta_").write(id.symbol()).write(" {").indent();
        write("Feng$Meta base").endStmt();
        for (var im : id.allMethods().values()) {
            writeVTableEntry(im, id);
        }
        dedent().write("} Feng$Meta_").write(id.symbol()).endStmt();
        write("#endif").newLine();
        // extern declaration for cross-module visibility (module mode, header only)
        if (header) {
            write("extern const Feng$Meta_").write(id.symbol())
                    .write(" Feng$meta_").write(id.symbol()).endStmt();
        }
        newLine();
    }

    /**
     * Emit concrete interface metadata type for a generic interface instantiation.
     */
    private void declareConcreteInterface(InterfaceDefinition id, DerivedType dt) {
        if (!declaredConcreteDecls.add(dt)) return;
        withMono(id.generic(), dt.generic(), () -> {
            // Ensure vtable entry types are declared before the meta struct
            for (var im : id.allMethods().values()) {
                var pt = im.prototype();
                pt.returnSet().use(this::ensureFieldTypeDecls);
                for (var t : pt.parameterSet().types()) ensureFieldTypeDecls(t);
            }
            var guard = guardName("FENG_STRUCT_Feng$Meta_" + mangledName(dt));
            write("#ifndef ").write(guard).newLine();
            write("#define ").write(guard).newLine();
            write("typedef struct Feng$Meta_").write(mangledName(dt)).write(" {").indent();
            write("Feng$Meta base").endStmt();
            for (var im : id.allMethods().values()) {
                writeVTableEntry(im, id);
            }
            dedent().write("} Feng$Meta_").write(mangledName(dt)).endStmt();
            write("#endif").newLine();
            // extern declaration for cross-module visibility (module mode, header only)
            if (header) {
                write("extern const Feng$Meta_").write(mangledName(dt))
                        .write(" Feng$meta_").write(mangledName(dt)).endStmt();
            }
            newLine();
        });
    }

    void declareClass(ClassDefinition cd) {
        if (cd.builtin()) return;
        // for single-file: always emit; for modules: header-only
        if (table.module.has() && !header) return;

        // generic class: emit concrete instantiations only, never the template
        if (!cd.generic().isEmpty()) {
            for (var dt : table.concreteInstantiations) {
                if (dt.def() == cd) {
                    if (cd.isFinal()) {
                        declareConcreteFinalClass(cd, dt);
                    } else {
                        declareConcreteNonFinalClass(cd, dt);
                    }
                }
            }
            return;
        }

        // memo: may already be emitted on demand by ensureFieldTypeDecls
        // (a concrete instantiation embedding this class by value, e.g. Box_Car)
        if (declaredClasses.contains(cd)) return;

        // Guard against re-entry from ensureFieldTypeDecls (when a class's
        // method return type is a value-typed generic class).
        // Do NOT add to declaredClasses yet — the BFS must still process this
        // class later so the struct body is actually emitted.
        if (enterClass != null) return;
        declaredClasses.add(cd);
        enterClass = cd;
        if (cd.isFinal()) {
            write("typedef struct ").write(cd.symbol()).write(" {").indent();
            for (var f : cd.fields().values()) write(f);
            dedent().write("} ").write(cd.symbol()).endStmt();
            newLine();
        } else {
            // flat layout: only one $meta at offset 0, all fields (inherited+own) in order
            write("typedef struct ").write(cd.symbol()).write(" {").indent();
            write("const Feng$Meta* $meta").endStmt();
            for (var f : cd.allFields().values()) write(f);
            dedent().write("} ").write(cd.symbol()).endStmt();
            newLine();

            // metadata type declaration
            // Ensure vtable entry types are declared before the meta struct
            for (var cm : cd.methods().values()) {
                if (!cm.generic().isEmpty()) continue;
                var pt = cm.prototype();
                pt.returnSet().use(this::ensureFieldTypeDecls);
                for (var t : pt.parameterSet().types()) ensureFieldTypeDecls(t);
            }
            // Ensure interface meta structs are declared before embedding them
            for (var ifaceDt : allConcreteIfaces(cd).values()) {
                var id = (InterfaceDefinition) ifaceDt.def();
                if (id.generic().isEmpty()) declareInterface(id);
                else declareConcreteInterface(id, ifaceDt);
            }
            write("typedef struct Feng$Meta_").write(cd.symbol()).write(" {").indent();
            cd.parent().use(p -> {
                if (p == ClassDefinition.ObjectClass || p.builtin())
                    write("Feng$Meta base").endStmt();
                else {
                    // if inheriting from a concrete generic instantiation, use mangled name
                    var inheritDt = cd.inherit().must();
                    if (!inheritDt.generic().isEmpty()) {
                        write("Feng$Meta_").write(mangledName(inheritDt)).write(" base").endStmt();
                    } else {
                        write("Feng$Meta_").write(p.symbol()).write(" base").endStmt();
                    }
                }
            }, () -> write("Feng$Meta base").endStmt());
            // class's own new/override methods
            for (var cm : cd.methods().values()) {
                if (!cm.generic().isEmpty()) continue; // skip method-level generics
                writeVTableEntry(cm, cd);
            }
            // interface vtables (concrete names for generic interfaces)
            for (var ifaceDt : allConcreteIfaces(cd).values()) {
                var iName = ifaceKey(ifaceDt);
                write("Feng$Meta_").write(iName)
                        .write(" i").write(iName).endStmt();
            }
            dedent().write("} Feng$Meta_").write(cd.symbol()).endStmt();
            // extern declaration for cross-module visibility (module mode, header only)
            if (header) {
                write("extern const Feng$Meta_").write(cd.symbol())
                        .write(" Feng$meta_").write(cd.symbol()).endStmt();
            }
            newLine();
        }
        // method prototypes — required for cross-module direct calls
        // (e.g. format_1 → std$bytes$BufferWriter$write)
        declareClassMethodProtos(cd);
        enterClass = null;
    }

    /**
     * Declare prototypes for all class methods (incl. operator/index macros).
     */
    private void declareClassMethodProtos(ClassDefinition cd) {
        for (var cm : cd.methods()) {
            if (!cm.generic().isEmpty()) continue; // method-level generics
            declareMethodProto(cd, cm);
        }
        for (var cm : cd.binaryOperators().values()) declareMethodProto(cd, cm);
        for (var cm : cd.unaryOperators().values()) declareMethodProto(cd, cm);
        cd.indexOperator().use(io -> {
            io.get().use(m -> declareMethodProto(cd, m));
            io.set().use(m -> declareMethodProto(cd, m));
        });
        newLine();
    }

    private void declareMethodProto(ClassDefinition cd, ClassMethod cm) {
        var pt = cm.prototype();
        pt.returnSet().use(this::write, () -> write("void"));
        write(' ').write(cd.symbol()).write(cm.name());
        write("(void *self");
        if (!pt.parameterSet().isEmpty()) {
            write(", ").write(pt.parameterSet());
        }
        write(')').endStmt();
    }

    /**
     * Emit struct definition for a concrete instantiation of a generic final class.
     */
    private void declareConcreteFinalClass(ClassDefinition cd, DerivedType dt) {
        if (!declaredConcreteDecls.add(dt)) return;
        withMono(cd.generic(), dt.generic(), () -> {
            // field-value dependencies must be complete before this struct
            for (var f : cd.fields().values()) {
                ensureFieldTypeDecls(f.type());
            }
            // Use #ifndef guard to prevent redefinition when the type
            // is already defined in an imported header
            var guard = guardName("FENG_STRUCT_" + mangledName(dt));
            write("#ifndef ").write(guard).newLine();
            write("#define ").write(guard).newLine();
            write("typedef struct ").writeMangledName(dt).write(" {").indent();
            var prevFinal = insideStructBody;
            insideStructBody = true;
            for (var f : cd.fields().values()) {
                write(f.type()).write(' ').write(f.name()).endStmt();
            }
            insideStructBody = prevFinal;
            dedent().write("} ").writeMangledName(dt).endStmt();
            write("#endif").newLine();
            newLine();
        });
    }

    // concrete class declarations already emitted (cross-class field deps
    // may emit an instantiation before its own class is visited)
    private final Set<DerivedType> declaredConcreteDecls = new HashSet<>();
    // non-generic class declarations already emitted (on-demand by field deps)
    private final Set<ClassDefinition> declaredClasses = new HashSet<>();

    /**
     * Ensure struct/typedef declarations a field type embeds BY VALUE are
     * emitted before the enclosing struct (e.g. Pair_Box_Int_Float embeds
     * struct Box_Int; Disk embeds Feng$Array_Box_IntPtr_4).
     */
    private void ensureFieldTypeDecls(TypeDeclarer ft) {
        ft = monoResolve(ft);
        if (ft instanceof FuncTypeDeclarer ftd) {
            // Function type: emit proto typedef inline so the typedef appears BEFORE
            // the struct that references it. Typedefs are also emitted by
            // declareConcreteTypes() but this handles non-generic cases and
            // is guarded by #ifndef to prevent double emission.
            var pt = ftd.prototype();
            // recurse into nested function types first
            pt.returnSet().use(t -> {
                if (t instanceof FuncTypeDeclarer f) ensureFieldTypeDecls(f);
            });
            for (var t : pt.parameterSet().types()) {
                if (t instanceof FuncTypeDeclarer f) ensureFieldTypeDecls(f);
            }
            // inline-emit the proto typedef (same pattern as ArrayTypeDeclarer)
            var key = protoKey(pt);
            if (emittedTypedefs.add(key)) {
                var guard = guardName("FENG_TYPEDEF_" + key);
                write("#ifndef ").write(guard).newLine();
                write("#define ").write(guard).newLine();
                write("typedef ");
                pt.returnSet().use(this::write, () -> write("void"));
                write(" (*Feng$").write(key).write(")(").write(pt.parameterSet()).write(")");
                endStmt();
                write("#endif").newLine();
            }
            return;
        }
        if (ft instanceof ArrayTypeDeclarer atd && atd.refer().none()) {
            ensureFieldTypeDecls(atd.element());
            // inline-emit the fixed-array typedef; the deferred flush later
            // re-emits the same guarded block which the preprocessor skips
            var key = "Array_" + typeKey(atd.element()) + "_" + atd.len();
            var guard = guardName("FENG_TYPEDEF_" + key);
            write("#ifndef ").write(guard).newLine();
            write("#define ").write(guard).newLine();
            write("typedef struct { ");
            writeTypeName(atd.element());
            write(" $values[").write(atd.len()).write("]; } Feng$").write(key).endStmt();
            write("#endif").newLine();
            return;
        }
        if (ft instanceof ArrayTypeDeclarer atd && atd.refer().has()) {
            // SRef or PRef array: register element type and inline-emit the array typedef
            ensureFieldTypeDecls(atd.element());
            if (atd.refer().get().isKind(PHANTOM)) {
                var key = "ArrayPRef_" + typeKey(atd.element());
                var guard = guardName("FENG_TYPEDEF_" + key);
                write("#ifndef ").write(guard).newLine();
                write("#define ").write(guard).newLine();
                write("typedef struct { ");
                writeTypeName(atd.element());
                write("* $values; Int64 $length; } Feng$").write(key).endStmt();
                write("#endif").newLine();
            } else {
                var key = "ArraySRef_" + typeKey(atd.element());
                var guard = guardName("FENG_TYPEDEF_" + key);
                write("#ifndef ").write(guard).newLine();
                write("#define ").write(guard).newLine();
                write("typedef struct { ");
                writeTypeName(atd.element());
                write("* $values; Int64 $length; } Feng$").write(key).endStmt();
                write("#endif").newLine();
                addCleanupType(atd.element());
            }
            return;
        }
        if (ft instanceof TupleTypeDeclarer ttd) {
            for (var et : ttd.elements()) ensureFieldTypeDecls(et);
            emitTupleType(ttd);
            return;
        }
        if (ft instanceof DerivedTypeDeclarer dtd && dtd.refer().none()
                && dtd.def() instanceof ClassDefinition fcd) {
            if (fcd.generic().isEmpty()) {
                // non-generic class embedded by value (e.g. Box_Car embeds
                // struct Car): emit its declaration now, bfs visit later no-ops
                declareClass(fcd);
                return;
            }
            var fdt = dtd.derivedType();
            if (!fdt.generic().isEmpty() && !fdt.hasTypeVar()) {
                if (fcd.isFinal()) declareConcreteFinalClass(fcd, fdt);
                else declareConcreteNonFinalClass(fcd, fdt);
            }
        }
    }

    /**
     * Emit struct + metadata type definitions for a concrete instantiation of a generic non-final class.
     */
    private void declareConcreteNonFinalClass(ClassDefinition cd, DerivedType dt) {
        if (!declaredConcreteDecls.add(dt)) return;
        withMono(cd.generic(), dt.generic(), () -> {
            // field-value dependencies must be complete before this struct
            for (var f : cd.allFields().values()) {
                ensureFieldTypeDecls(f.type());
            }
            // struct: flat layout with $meta + allFields
            // Use #ifndef guard to prevent redefinition when the type
            // is already defined in an imported header
            var structGuard = guardName("FENG_STRUCT_" + mangledName(dt));
            write("#ifndef ").write(structGuard).newLine();
            write("#define ").write(structGuard).newLine();
            write("typedef struct ").writeMangledName(dt).write(" {").indent();
            write("const Feng$Meta* $meta").endStmt();
            var prevNF = insideStructBody;
            insideStructBody = true;
            for (var f : cd.allFields().values()) {
                write(f.type()).write(' ').write(f.name()).endStmt();
            }
            insideStructBody = prevNF;
            dedent().write("} ").writeMangledName(dt).endStmt();
            write("#endif").newLine();
            newLine();

            // metadata type declaration
            // Ensure vtable entry types are declared before the meta struct
            for (var cm : cd.methods().values()) {
                if (!cm.generic().isEmpty()) continue;
                var pt = cm.prototype();
                pt.returnSet().use(this::ensureFieldTypeDecls);
                for (var t : pt.parameterSet().types()) ensureFieldTypeDecls(t);
            }
            // Ensure interface meta structs are declared before embedding them
            for (var ifaceDt : allConcreteIfaces(cd).values()) {
                var id = (InterfaceDefinition) ifaceDt.def();
                if (id.generic().isEmpty()) declareInterface(id);
                else declareConcreteInterface(id, ifaceDt);
            }

            var metaGuard = guardName("FENG_STRUCT_Feng$Meta_" + mangledName(dt));
            write("#ifndef ").write(metaGuard).newLine();
            write("#define ").write(metaGuard).newLine();
            write("typedef struct Feng$Meta_").write(mangledName(dt)).write(" {").indent();
            cd.parent().use(p -> {
                if (p == ClassDefinition.ObjectClass)
                    write("Feng$Meta base").endStmt();
                else {
                    // parent may also be generic → need mangled name for parent
                    write("Feng$Meta_").write(parentMangledName(cd, dt))
                            .write(" base").endStmt();
                }
            }, () -> write("Feng$Meta base").endStmt());
            // class's own methods (not inherited)
            for (var cm : cd.methods().values()) {
                if (!cm.generic().isEmpty()) continue; // skip method-level generics
                writeVTableEntry(cm, cd);
            }
            // interface vtables — use concrete mangled names for all interfaces (direct + inherited)
            for (var ifaceDt : allConcreteIfaces(cd).values()) {
                var iName = ifaceKey(ifaceDt);
                write("Feng$Meta_").write(iName)
                        .write(" i").write(iName).endStmt();
            }
            dedent().write("} Feng$Meta_").write(mangledName(dt)).endStmt();
            write("#endif").newLine();
            // extern declaration for cross-module visibility (module mode, header only)
            if (header) {
                write("extern const Feng$Meta_").write(mangledName(dt))
                        .write(" Feng$meta_").write(mangledName(dt)).endStmt();
            }
            newLine();
        });
    }

    /**
     * Get the mangled name for the parent class of a generic instantiation.
     */
    private String parentMangledName(ClassDefinition cd, DerivedType dt) {
        var p = cd.parent().must();
        if (p.generic().isEmpty()) return p.symbol().toString();
        // parent is also generic: resolve CD's type vars to concrete args positionally
        var inheritDt = cd.inherit().must(); // DerivedType for parent in cd's definition
        // currentTypeMap is already set by caller's withMono block
        var parentArgs = inheritDt.generic().stream()
                .map(this::monoResolve).toList();
        // Build mangled name including module prefix
        var parentDt = p.link(new TypeArguments(Position.ZERO, parentArgs));
        return mangledName(parentDt);
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
        // Use dt.generic() (cd's concrete args) rather than ambient currentTypeMap so this
        // works both inside and outside a monomorphization context.
        var ancArgs = anc.generic().stream()
                .map(tp -> {
                    var gtd = new GenericTypeDeclarer(tp.pos(), new GenericType(tp.pos(), tp));
                    return resolveArgFromAncestor(cd, anc, gtd, dt.generic());
                }).toList();
        var typeArgs = new TypeArguments(anc.generic().pos(), ancArgs);
        var result = new DerivedType(anc.symbol().pos(), anc.symbol(), typeArgs);
        result.def(anc);
        return result;
    }

    /**
     * Emit method implementations for a concrete instantiation of a generic final class.
     */
    private void implConcreteFinalClass(ClassDefinition cd, DerivedType dt) {
        withMono(cd.generic(), dt.generic(), () -> {
            for (var cm : cd.methods()) {
                if (!cm.generic().isEmpty()) continue; // skip method-level generics
                implConcreteMethod(cm, dt);
            }
        });
    }

    private void implConcreteMethod(ClassMethod cm, DerivedType dt) {
        var pt = cm.prototype();
        pt.returnSet().use(this::write, () -> write("void"));
        write(' ').writeMangledName(dt).write('$');
        write(cm.name());
        write('(');
        write("void *self");
        var ps = pt.parameterSet();
        if (!ps.isEmpty()) {
            write(", ");
            write(ps);
        }
        write(") {").indent();
        writeMangledName(dt).write(" *const _self = self; ").newLine();
        write(cm.procedure().must());
        dedent().write('}').newLine();
    }

    /**
     * Generate a concrete instantiation of a method-level generic, e.g.
     * {@code SealedBox_Int$map_Bool(struct SealedBox_Int* self, Bool $g)}.
     * Class + method type parameters are combined into a single mono context
     * so that both {@code X→Int} and {@code G→Bool} resolve.
     */
    private void implConcreteMethodGeneric(ClassDefinition cd, DerivedType dt,
                                           ClassMethod cm, TypeArguments methodArgs) {
        withMonoComposed(cd.generic(), dt.generic(), cm.generic(), methodArgs, () -> {
            var pt = cm.prototype();
            pt.returnSet().use(this::write, () -> write("void"));
            write(' ').writeMangledName(dt).write('$').write(cm.name())
                    .write('_').write(methodArgs.stream()
                            .map(this::typeKey).collect(Collectors.joining("_")));
            write('(');
            write("void *self");
            var ps = pt.parameterSet();
            if (!ps.isEmpty()) {
                write(", ");
                write(ps);
            }
            write(") {").indent();
            writeMangledName(dt).write(" *const _self = self; ").newLine();
            write(cm.procedure().must());
            dedent().write('}').newLine();
        });
    }

    /**
     * Emit method implementations for a concrete instantiation of a generic non-final class.
     */
    private void implConcreteNonFinalClass(ClassDefinition cd, DerivedType dt) {
        withMono(cd.generic(), dt.generic(), () -> {
            for (var cm : cd.methods()) {
                if (!cm.generic().isEmpty()) continue; // skip method-level generics
                implConcreteMethod(cm, dt);
            }
        });
        // generate method-level generic instantiations for this class/dt
        for (var mi : table.concreteMethodInsts) {
            if (mi.classDt().equals(dt) && emittedMethodInsts.add(mi)) {
                implConcreteMethodGeneric(cd, dt, mi.method(), mi.methodArgs());
                newLine();
            }
        }
    }

    private CGenerator write(ClassField cf) {
        assert enterClass != null;
        var prev = insideStructBody;
        insideStructBody = true;
        write(cf.type()).write(' ');
        insideStructBody = prev;
        return write(cf.name()).endStmt();
    }

    private void implClass(ClassDefinition cd) {
        if (cd.builtin()) return;
        // generic classes: generate concrete instantiations
        if (!cd.generic().isEmpty()) {
            if (table.module.has() && header) return; // concrete methods in source only
            for (var dt : table.concreteInstantiations) {
                if (dt.def() == cd) {
                    if (cd.isFinal()) {
                        implConcreteFinalClass(cd, dt);
                    } else {
                        implConcreteNonFinalClass(cd, dt);
                    }
                }
            }
            return;
        }
        if (table.module.has()) {
            if (header == cd.generic().isEmpty()) return;
        }
        assert enterClass == null;
        enterClass = cd;
        for (var cm : cd.methods()) implMethod(cm);
        // operator methods: use the macro id (feng$macro$operator$xxx) as a
        // valid C name; call sites dispatch directly (write(BinaryExpression))
        for (var cm : cd.binaryOperators().values()) implMethod(cm);
        for (var cm : cd.unaryOperators().values()) implMethod(cm);
        // index operator macros (feng$macro$index$get/set)
        cd.indexOperator().use(io -> {
            io.get().use(this::implMethod);
            io.set().use(this::implMethod);
        });
        enterClass = null;
    }

    private void implMethod(ClassMethod cm) {
        implMethod(cm, () -> write(cm.name()));
    }

    private void implMethod(ClassMethod cm, Runnable naming) {
        assert enterClass != null;
        write(enterClass.generic());
        write(cm.generic());
        // method signature: RetType Feng$Class$method(void* self, ...params)
        var pt = cm.prototype();
        pt.returnSet().use(this::write, () -> write("void"));
        write(' ').write(enterClass.symbol());
        naming.run();
        write('(');
        write("void *self");
        var ps = pt.parameterSet();
        if (!ps.isEmpty()) {
            write(", ");
            write(ps);
        }
        write(") {").indent();
        write(enterClass.symbol()).write(" *const _self = self; ").newLine();
        write(cm.procedure().must());
        dedent().write('}').newLine();
    }

    // ---- enum ----

    CGenerator enumName(EnumDefinition ed) {
        return write("Feng$Enum_").write(ed.symbol());
    }

    void enumDefinition() {
        if (header) return;
        writeComment("enum definition");
        for (var ed : table.enumList) visitEnum(ed);
        newLine();
    }

    void visitEnum(EnumDefinition ed) {
        write("static Feng$Enum ").enumName(ed).write('[')
                .write(ed.size()).write("] = {").indent();
        for (var v : ed.values()) {
            write('{').write(v.val()).write(", {");
            write(v.nameLit()).write(".array.$values, ").write(v.nameLit().length())
                    .write("}},").newLine();
        }
        dedent().write("}").endStmt();
    }

    // ---- struct/union ----

    void structureDefinition() {
        if (table.module.has() && !header) return;
        writeComment("structure definitions");
        for (var sd : table.dagStructures) write(sd);
        newLine();
    }

    private CGenerator write(StructureField sf) {
        var prev = insideStructBody;
        insideStructBody = true;
        write(sf.type()).write(' ').write(sf.name());
        insideStructBody = prev;
        if (sf.bitfield().has()) write(':').write(sf.bits());
        // @Align({value=n}) → __attribute__((aligned(n)))
        if (sf.align() > 0) {
            write(" __attribute__((aligned(").write(sf.align()).write(")))");
        }
        return endStmt();
    }

    private CGenerator write(StructureDefinition sd) {
        if (sd.cType() && !sd.anonymous()) return this; // named C-imported: handled by bridge header
        // Ensure field-type typedefs (FixedArray, Tuple, func-proto, …) are
        // emitted before the struct body.  DAG topological order guarantees
        // element types are already complete by this point.
        for (var sf : sd.fields()) ensureFieldTypeDecls(sf.type());
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
        for (var sf : sd.fields()) write(sf);
        dedent().write("} ").write(sd.symbol()).endStmt();
        if (p > 0) {
            write("#pragma pack(pop)").newLine();
        }
        if (!sd.cType() && sd.layout().has()) {
            write("_Static_assert(sizeof(").write(sd.symbol())
                    .write(") == ")
                    .write(sd.layout().must().size()).write(", \"size check\")");
            endStmt();
        }
        return this;
    }

    // ---- string cache ----

    private void literalStringCache() {
        if (header) return;
        writeComment("string cache");
        var list = table.stringCache.keySet().stream()
                .sorted(Comparator.comparingInt(StringLiteral::id)).toList();
        for (var sl : list) {
            write("static struct { Feng$Header header; struct { Byte $values[")
                    .write(sl.length()).write("]; } array; } ");
            literalString(sl);
            write(" = {{NULL, 1}, {{");
            for (byte b : sl.value()) write(b).write(',');
            write("}}}").endStmt();
        }
        newLine();
    }

    // ---- functions ----

    /**
     * Emit extern declarations for imported generic function instantiations.
     * Must be called before class method bodies that reference these functions.
     */
    private void declareExternFuncInsts() {
        if (!table.externFuncInsts.isEmpty()) {
            writeComment("imported generic function declarations");
            for (var ent : table.externFuncInsts.entrySet()) {
                var fi = ent.getValue();
                withMono(fi.fd().generic(), fi.args(), () -> {
                    fi.fd().prototype().returnSet().use(this::write, () -> write("void"));
                    write(' ').write(ent.getKey());
                    write('(').write(fi.fd().prototype().parameterSet()).write(')');
                    endStmt();
                });
                newLine();
            }
            newLine();
        }
    }

    private void declareProtoTypedefs() {
        if (table.module.has() && !header) return;
        writeComment("prototype definition");
        table.dagPrototypes.bfs(pd -> {
            if (pd.prototype().hasTypeVar()) return;
            write("typedef ");
            write(pd.symbol(), pd.prototype());
            endStmt();
        });
        // Fallback: flush any anonymous prototype typedefs
        // registered by emitProtoType during code generation
        for (var pt : table.pendingProtoTypedefs) {
            var key = protoKey(pt);
            var guard = guardName("FENG_TYPEDEF_" + key);
            write("#ifndef ").write(guard).newLine();
            write("#define ").write(guard).newLine();
            write("typedef ");
            pt.returnSet().use(this::write, () -> write("void"));
            write(" (*Feng$").write(key).write(")(").write(pt.parameterSet()).write(")");
            endStmt();
            write("#endif").newLine();
        }
        table.pendingProtoTypedefs.clear();
        newLine();
    }

    void declareFunction() {
        if (table.module.has() && !header) {
            return;
        }
        writeComment("function declaration");
        for (var fd : table.functionList) {
            if (table.test && table.main.has()
                    && fd == table.main.must()) continue;
            declareFunction(fd);
            newLine();
        }
        // declare concrete generic function instantiations
        for (var fi : table.concreteFuncInsts) {
            declareConcreteFunc(fi.fd(), fi.args());
            newLine();
        }
        // declare imported (cross-module) generic instantiations from
        // their FuncInstantiation entries (bodies live in the defining module)
        for (var ent : table.externFuncInsts.entrySet()) {
            var fi = ent.getValue();
            withMono(fi.fd().generic(), fi.args(), () -> {
                fi.fd().prototype().returnSet().use(this::write, () -> write("void"));
                write(' ').write(ent.getKey());
                write('(').write(fi.fd().prototype().parameterSet()).write(')');
                endStmt();
            });
            newLine();
        }
        newLine();
    }

    void declareFunction(FunctionDefinition fd) {
        if (!fd.generic().isEmpty()) return; // generic: concrete instantiations only
        if (fd.procedure().none()) return; // C-imported: handled by bridge header
        write(fd.symbol(), fd.prototype());
        endStmt();
    }

    /**
     * Declare a concrete instantiation of a generic function in the header.
     */
    private void declareConcreteFunc(FunctionDefinition fd, TypeArguments args) {
        withMono(fd.generic(), args, () -> {
            // return type (instantiated via monoResolve)
            fd.prototype().returnSet().use(this::write, () -> write("void"));
            write(' ');
            // mangled name: $make_Int
            fd.symbol().module().use(mp -> write(mp.toString()));
            write('$').write(fd.symbol().name().value())
                    .write('_').write(args.stream()
                            .map(this::typeKey)
                            .collect(Collectors.joining("_")));
            write('(').write(fd.prototype().parameterSet()).write(')');
            endStmt();
        });
    }

    /**
     * Pre-scan a function definition to register C type declarations
     * Monomorphization already handles type discovery.
     */
    private void registerClassCleanup(TypeDeclarer td) {
        var ref = td.maybeRefer();
        if (!ref.has() || !ref.get().isKind(STRONG)) return;
        classCleanupFnFor(td);
    }

    /**
     * Register (if needed) the per-class cleanup function for a strong class-ref type.
     * Handles both plain classes and concrete generic instantiations.
     * Returns the cleanup function name, or null when no per-class cleanup applies.
     */
    private String classCleanupFnFor(TypeDeclarer td) {
        td = monoResolve(td);
        // unresolved generic — cleanup registered per concrete instantiation
        if (td.hasTypeVar()) return null;
        if (!(td instanceof DerivedTypeDeclarer dtd)
                || !(dtd.def() instanceof ClassDefinition cd)) return null;
        var dt = dtd.derivedType();
        var needCascade = !cd.isFinal() && cd.allFields().values().stream()
                .map(cf -> resolveFromMap(cf.type(), buildTypeMap(cd.generic(), dt.generic())))
                .anyMatch(ft -> ft.maybeRefer().match(r -> r.isKind(STRONG)));
        if (!cd.resource() && !needCascade) return null;
        var ck = "Feng$cleanup_" + typeKey(td);
        if (cd.generic().isEmpty()) needClassCleanup(cd, ck);
        else needConcreteClassCleanup(cd, dt, ck);
        return ck;
    }

    /**
     * Walk statements to register cleanup types for local variable declarations.
     * Type discovery (typedef emission) is handled by Monomorphization + declareConcreteTypes().
     */
    private void preScanCleanupStmts(Statement stmt) {
        if (stmt instanceof DeclarationStatement ds) {
            for (var v : ds.variables()) {
                registerClassCleanup(v.type().must());
            }
        } else if (stmt instanceof BlockStatement bs) {
            for (var s : bs.list()) preScanCleanupStmts(s);
        } else if (stmt instanceof IfStatement is) {
            is.init().use(this::preScanCleanupStmts);
            preScanCleanupStmts(is.yes());
            is.not().use(this::preScanCleanupStmts);
        } else if (stmt instanceof ForStatement fs) {
            if (fs instanceof ConditionalForStatement cfs) {
                cfs.initializer().use(this::preScanCleanupStmts);
                preScanCleanupStmts(cfs.body());
            }
        } else if (stmt instanceof SwitchStatement ss) {
            for (var br : ss.branches()) preScanCleanupStmts(br);
        } else if (stmt instanceof TryStatement ts) {
            preScanCleanupStmts(ts.body());
            for (var cc : ts.catchClauses()) {
                preScanCleanupStmts(cc.body());
            }
            ts.finallyClause().use(this::preScanCleanupStmts);
        }
    }


    void functionDefinition() {
        if (header) return;

        // Register cleanup for local variable types in function bodies.
        // Type discovery (typedef emission) is handled by Monomorphization + declareConcreteTypes().
        for (var fd : table.functionList) {
            if (fd.builtin()) continue;
            for (var p : fd.prototype().parameterSet()) {
                if (p instanceof FixedParameter fp) {
                    registerClassCleanup(fp.type());
                }
            }
            fd.procedure().use(proc -> preScanCleanupStmts(proc.body()));
        }
        table.main.use(fd -> {
            for (var p : fd.prototype().parameterSet()) {
                if (p instanceof FixedParameter fp) {
                    registerClassCleanup(fp.type());
                }
            }
            fd.procedure().use(proc -> preScanCleanupStmts(proc.body()));
        });

        // Flush cleanup forward declarations discovered during pre-scan,
        // so that FENG$DEC references in function bodies can resolve.
        {
            var fwds = new ArrayList<>(classCleanupForwards);
            classCleanupForwards.clear();
            for (var r : fwds) r.run();
        }

        // Monomorphization has already discovered all concrete generic function
        // instantiations. Forward-declare them before any function body.
        var allFi = new LinkedHashSet<>(table.concreteFuncInsts);
        if (!allFi.isEmpty()) {
            for (var fi : allFi) {
                if (declaredFuncInsts.add(fi)) {
                    declareConcreteFunc(fi.fd(), fi.args());
                    newLine();
                }
            }
            newLine();
        }

        // forward-declare method-level generic instantiations
        if (!table.concreteMethodInsts.isEmpty()) {
            for (var mi : table.concreteMethodInsts) {
                if (mi.method().generic().isEmpty()) continue;
                declareConcreteMethodGeneric(mi);
                newLine();
            }
            newLine();
        }

        writeComment("function definition");
        for (var fd : table.functionList) {
            if (fd.builtin()) continue;
            // In test mode, skip user's main — test runner provides its own
            if (table.test && table.main.has()
                    && fd == table.main.must()) continue;
            implFunc(fd);
            newLine();
        }
        // generate concrete generic function bodies (all forward-declared above)
        if (!allFi.isEmpty()) {
            writeComment("concrete generic functions");
            for (var fi : allFi) {
                implConcreteFunc(fi.fd(), fi.args());
                newLine();
            }
            newLine();
        }
        // generate concrete method-level generic bodies
        if (!table.concreteMethodInsts.isEmpty()) {
            writeComment("concrete generic methods");
            for (var mi : table.concreteMethodInsts) {
                if (!emittedMethodInsts.add(mi)) continue;
                implConcreteMethodGeneric(
                        (ClassDefinition) mi.classDt().def(),
                        mi.classDt(), mi.method(), mi.methodArgs());
                newLine();
            }
            newLine();
        }
        // generate concrete class method bodies for imported generic classes
        var localClasses = new HashSet<ClassDefinition>();
        for (var cd : table.dagClasses) localClasses.add(cd);
        var importedConcreteClasses = new ArrayList<DerivedType>();
        for (var dt : table.concreteInstantiations) {
            if (dt.def() instanceof ClassDefinition cd
                    && !localClasses.contains(cd) && !cd.generic().isEmpty()
                    && !dt.generic().isEmpty() && !dt.hasTypeVar()) {
                importedConcreteClasses.add(dt);
            }
        }
        // first pass: register cleanup types for method bodies of imported classes
        for (var dt : importedConcreteClasses) {
            if (dt.def() instanceof ClassDefinition cd
                    && !localClasses.contains(cd) && !cd.generic().isEmpty()
                    && !dt.generic().isEmpty() && !dt.hasTypeVar()) {
                withMono(cd.generic(), dt.generic(), () -> {
                    for (var cm : cd.methods()) {
                        if (!cm.generic().isEmpty()) continue;
                        cm.procedure().use(proc -> preScanCleanupStmts(proc.body()));
                    }
                });
            }
        }
        // second pass: generate method bodies
        for (var dt : importedConcreteClasses) {
            var cd = (ClassDefinition) dt.def();
            if (!header) {
                if (cd.isFinal()) implConcreteFinalClass(cd, dt);
                else implConcreteNonFinalClass(cd, dt);
            }
        }
        newLine();
        if (table.test && !table.testcases.isEmpty()) {
            writeTestRunner(table.testcases);
        } else {
            table.main.use(this::writeMain);
        }
        newLine();
    }

    void writeTestRunner(List<Symbol> testcases) {
        if (header) return;

        writeComment("auto-generated test runner");
        table.module.use(fm -> {
            write("#include \"").write(fm.path().filename())
                    .write(".h\"").newLine();
        });
        write("#include <stdio.h>").newLine();
        newLine();

        // Test entry struct
        write("typedef struct {").indent().newLine();
        write("const char* name;").newLine();
        write("void (*func)(void);").newLine();
        dedent().write("} Feng$TestEntry;").newLine();
        newLine();

        // Test registry
        write("static Feng$TestEntry Feng$tests[] = {").indent().newLine();
        for (var ts : testcases) {
            if (!table.testFilter.isEmpty()
                    && !table.testFilter.contains(ts.name().toString())) {
                continue;
            }
            write("{\"").write(ts.name().toString())
                    .write("\", &").write(ts).write("},").newLine();
        }
        write("{NULL, NULL}").newLine();
        dedent().write("};").newLine();
        newLine();

        // main function
        write("int main(void) {").indent().newLine();
        write("int passed = 0;").newLine();
        write("int failed = 0;").newLine();
        newLine();
        write("for (int i = 0; Feng$tests[i].name != NULL; i++) {").indent().newLine();
        write("printf(\"  RUN  %s ... \", Feng$tests[i].name);").newLine();
        write("fflush(stdout);").newLine();
        newLine();
        write("volatile Feng$ExFrame _frame = {.prev = Feng$ex_top};").newLine();
        write("Feng$ex_top = (Feng$ExFrame*)&_frame;").newLine();
        newLine();
        write("if (setjmp(*(jmp_buf*)&_frame.buf) == 0) {").indent().newLine();
        write("Feng$tests[i].func();").newLine();
        write("Feng$ex_top = _frame.prev;").newLine();
        write("printf(\"PASS\\n\");").newLine();
        write("passed++;").newLine();
        dedent().write("} else {").newLine();
        indent().write("Feng$ex_top = _frame.prev;").newLine();
        write("printf(\"FAIL\\n\");").newLine();
        write("failed++;").newLine();
        dedent().write("}").newLine();
        dedent().write("}").newLine();
        newLine();
        write("printf(\"\\nResults: %d passed, %d failed, %d total\\n\",").newLine();
        write("       passed, failed, passed + failed);").newLine();
        write("return failed > 0 ? 1 : 0;").newLine();
        dedent().write("}").newLine();
        newLine();
    }

    void writeMain(FunctionDefinition main) {
        writeComment("entry function");
        implFunc(main);
        newLine();
        if (header) return;

        if (!main.prototype().parameterSet().isEmpty()) {
            write("#define FENG_MAIN_HAS_ARGS").newLine();
            newLine();
        }

        try (var is = getResource(mainFile);
             var ir = new InputStreamReader(Objects.requireNonNull(is));
             var r = new BufferedReader(ir)) {
            r.lines().forEach(line -> write(line).newLine());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void implFunc(FunctionDefinition fd) {
        if (!fd.generic().isEmpty()) return; // generic: concrete instantiations only
        if (fd.procedure().none()) return;   // metadata-only (e.g. from C header): no body
        write(fd.symbol(), fd.prototype());
        write(' ').write(fd.procedure().must());
    }

    /**
     * Generate a concrete instantiation of a generic function.
     */
    private void implConcreteFunc(FunctionDefinition fd, TypeArguments args) {
        var fi = new FuncInstantiation(fd, args);
        // emit forward declaration if not already declared
        if (declaredFuncInsts.add(fi)) {
            declareConcreteFunc(fd, args);
            newLine();
        }
        // pre-scan the function body in mono context to register cleanup types
        withMono(fd.generic(), args, () -> {
            for (var p : fd.prototype().parameterSet()) {
                if (p instanceof FixedParameter fp) {
                    registerClassCleanup(fp.type());
                }
            }
            fd.procedure().use(proc -> preScanCleanupStmts(proc.body()));
        });
        withMono(fd.generic(), args, () -> {
            // return type (instantiated via monoResolve)
            fd.prototype().returnSet().use(this::write, () -> write("void"));
            write(' ');
            // mangled name: $make_Int
            fd.symbol().module().use(mp -> write(mp.toString()));
            write('$').write(fd.symbol().name().value())
                    .write('_').write(args.stream()
                            .map(this::typeKey)
                            .collect(Collectors.joining("_")));
            write('(').write(fd.prototype().parameterSet()).write(')');
            write(' ').write(fd.procedure().must());
        });
    }

    /**
     * Emit forward declaration for a concrete method-level generic instantiation.
     *
     * @see #implConcreteMethodGeneric
     */
    private void declareConcreteMethodGeneric(MethodInstantiation mi) {
        var cd = (ClassDefinition) mi.classDt().def();
        withMonoComposed(cd.generic(), mi.classDt().generic(),
                mi.method().generic(), mi.methodArgs(), () -> {
                    var pt = mi.method().prototype();
                    pt.returnSet().use(this::write, () -> write("void"));
                    write(' ').writeMangledName(mi.classDt()).write('$').write(mi.method().name())
                            .write('_').write(mi.methodArgs().stream()
                                    .map(this::typeKey).collect(Collectors.joining("_")));
                    write('(');
                    write("void *self");
                    var ps = pt.parameterSet();
                    if (!ps.isEmpty()) {
                        write(", ");
                        write(ps);
                    }
                    write(')').endStmt();
                });
    }

    private CGenerator write(Procedure proc) {
        write('{').indent();
        // for exceptions
        write("_feng_fn_label:;").newLine();
        write((Statement) proc.body());
        if (noTerminal(proc.body().list())) exitScope(proc);
        dedent().write('}').newLine();
        return this;
    }

    // ---- global variables ----

    private CGenerator write(GlobalVariable v) {
        if (v.export()) {
            if (header) write("extern ");
        } else {
            if (header) return this;
            write("static ");
        }
        var t = v.type().must();
        declare(v);
        if (v.export() && header) return endStmt();
        write(" = ");
        // runtime initializer (new()/calls/block expressions) is not a valid
        // C static initializer — default-init here, real init in constructor
        if (v.value().has() && isRuntimeInit(v.value().must())) {
            var e = v.value().must();
            if (t instanceof ArrayTypeDeclarer atd && atd.refer().has())
                write("{NULL, 0}");
            else if (t.maybeRefer().has()) write("NULL");
            else write("{}");
            globalInits.add(() -> {
                varName(v).write(" = ");
                writeValue(e, t);
                endStmt();
            });
            return endStmt();
        }
        v.value().use(e -> writeValue(e, t), () -> {
            if (t.maybeRefer().has()) write("NULL");
            else write("{}");
        });
        return endStmt();
    }

    /**
     * Whether the initializer needs runtime evaluation (not a valid C
     * compile-time constant for static storage).
     */
    private boolean isRuntimeInit(Expression e) {
        if (e instanceof NewExpression || e instanceof CallExpression
                || e instanceof MethodExpression || e instanceof BlockExpression)
            return true;
        // reading another global variable's VALUE is not a C constant expression
        if (e instanceof VariableExpression)
            return true;
        if (e instanceof TupleExpression te)
            return te.elements().stream().anyMatch(this::isRuntimeInit);
        if (e instanceof ObjectExpression oe)
            return oe.entries().values().stream().anyMatch(this::isRuntimeInit);
        if (e instanceof ArrayExpression ae)
            return ae.elements().stream().anyMatch(this::isRuntimeInit);
        if (e instanceof BinaryExpression be)
            return isRuntimeInit(be.left()) || isRuntimeInit(be.right());
        if (e instanceof UnaryExpression ue)
            return isRuntimeInit(ue.operand());
        if (e instanceof ParenExpression pe)
            return isRuntimeInit(pe.child());
        return false;
    }

    /**
     * Emit the constructor that performs runtime global initialization.
     */
    private void emitGlobalInits() {
        if (globalInits.isEmpty()) return;
        write("__attribute__((constructor)) static void Feng$globals_init(void) {")
                .indent();
        var batch = new ArrayList<>(globalInits);
        globalInits.clear();
        for (var r : batch) r.run();
        dedent().write('}').newLine();
    }

    private void declareGlobalVar(List<GlobalVariable> vars) {
        // First pass: forward-declare non-export variables so method bodies can reference them
        for (var v : vars) {
            if (v.export()) continue;
            write("static ").write(v.type().must()).write(' ').varName(v).endStmt();
        }
        // Second pass: full definitions
        vars.forEach(this::write);
        newLine();
    }

    private void declareGlobalVar(DAGGraph<GlobalVariable> vars) {
        // First pass: forward-declare non-export variables so method bodies can reference them
        for (var v : vars.all()) {
            if (v.export()) continue;
            write("static ").write(v.type().must()).write(' ').varName(v).endStmt();
        }
        // Second pass: full definitions (DAG order for dependencies)
        vars.bfs(this::write);
        newLine();
    }

    // ===================================================================
    //  Entry point — orchestrates the full output pipeline
    // ===================================================================

    @Override
    public void write() {
        definePre();
        includeHeaders();
        newLine();

        declareType();

        // Forward-declare ALL concrete class/struct struct tags BEFORE
        // declareConcreteTypes(), so that FixedArray/ArrayRef/Tuple typedefs
        // can reference class/struct types that haven't been fully defined yet.
        declareConcreteStructForwards();

        // Emit typedefs for all concrete types (arrays, tuples, generic classes)
        // discovered by Monomorphization, in DAG topological order.
        // This replaces the old registerType→pending/deferred mechanism for
        // array/tuple types. Class struct bodies are still handled by
        // classesDefinition() below.
        declareConcreteTypes();

        // Register cleanup for class fields in both header & source passes
        for (var cd : table.dagClasses) {
            for (var cf : cd.fields().values()) {
                registerClassCleanup(cf.type());
                if (cf.type() instanceof ArrayTypeDeclarer atd
                        && atd.refer().match(r -> r.isKind(STRONG)))
                    addCleanupType(atd.element());
            }
        }

        // Emit extern declarations for imported generic functions BEFORE
        // class method bodies that reference them
        declareExternFuncInsts();

        literalStringCache();
        enumDefinition();

        structureDefinition();
        declareProtoTypedefs();
        classesDefinition();
        // Emit deferred concrete types that need complete struct definitions
        // (must be after classesDefinition which emits struct bodies)
        declareConcreteTypesDeferred();

        declareFunction();

        writeComment("global const");
        declareGlobalVar(table.constVars);

        // Emit forward declarations for cleanup functions BEFORE classMethods()
        // so that FENG$DEC references in method bodies can resolve the function names.
        if (!header) emitCleanupForwardDecls();

        classMethods();
        functionDefinition();

        metaDefinitions();

        writeComment("global variable");
        declareGlobalVar(table.dagVars);
        // runtime global initializers → GCC constructor (runs before main)
        if (!header) emitGlobalInits();

        // emit cleanup functions at end of file
        if (!header) {
            emitCleanupFunctions();
            emitClassCleanups();
        }

        endFile();
    }
}
