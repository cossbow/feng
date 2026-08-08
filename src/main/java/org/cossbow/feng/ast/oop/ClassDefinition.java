package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.Declare;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.ast.proc.FixedParameter;
import org.cossbow.feng.ast.proc.ParameterSet;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.*;
import java.util.stream.Stream;

final
public class ClassDefinition extends ObjectDefinition {
    // The following are the properties defined in the syntax:
    /**
     * Final classes do not allow inheritance, being inherited,
     * or abstraction. Without dynamic features, some optimizations
     * can be made, such as omitting type pointers when allocating
     * memory.
     */
    private final boolean isFinal;
    /**
     * Inheritance declaration is optional, only the final class
     * and built-in Object class are empty. In other cases,
     * if not declared, it will automatically be set as a
     * symbolic reference to the Object class.
     */
    private final Optional<DerivedType> inherit;
    /**
     * Implement the interface set for declaration.
     */
    private final SymbolMap<DerivedType> impl;
    /**
     * Fields Definition
     */
    private final IdentifierMap<ClassField> fields;
    /**
     * Methods Definition
     */
    private final IdentifierMap<ClassMethod> methods;
    private final MacroTable macros;

    public ClassDefinition(Position pos,
                           Modifier modifier,
                           Symbol symbol,
                           TypeParameters generic,
                           boolean isFinal,
                           Optional<DerivedType> inherit,
                           SymbolMap<DerivedType> impl,
                           IdentifierMap<ClassField> fields,
                           IdentifierMap<ClassMethod> methods,
                           MacroTable macros) {
        super(pos, modifier, symbol, generic, TypeDomain.CLASS);
        this.isFinal = isFinal;
        this.inherit = inherit;
        this.impl = impl;
        this.fields = fields;
        this.methods = methods;
        this.macros = macros;
    }

    public ClassDefinition(Position pos,
                           Modifier modifier,
                           Symbol symbol,
                           TypeParameters generic,
                           Optional<DerivedType> inherit,
                           SymbolMap<DerivedType> impl,
                           IdentifierMap<ClassField> fields,
                           IdentifierMap<ClassMethod> methods,
                           MacroTable macros) {
        this(pos, modifier, symbol, generic, false,
                inherit, impl, fields, methods, macros);
    }

    public ClassDefinition(Position pos,
                           Modifier modifier,
                           Symbol symbol,
                           TypeParameters generic,
                           IdentifierMap<ClassField> fields,
                           IdentifierMap<ClassMethod> methods,
                           MacroTable macros) {
        this(pos, modifier, symbol, generic, true, Optional.empty(),
                new SymbolMap<>(), fields, methods, macros);
    }

    public boolean isFinal() {
        return isFinal;
    }

    public Optional<DerivedType> inherit() {
        return inherit;
    }

    public SymbolMap<DerivedType> impl() {
        return impl;
    }

    public IdentifierMap<ClassField> fields() {
        return fields;
    }

    public IdentifierMap<ClassMethod> methods() {
        return methods;
    }

    public MacroTable macros() {
        return macros;
    }

    public boolean newable() {
        return true;
    }

    //

    /**
     * After analyzing the {@link #inherit}, cache will be defined here
     */
    private final Lazy<ClassDefinition> parent = Lazy.nil();
    /**
     * times of inherited by other classes
     */
    private int inherited = 0;
    /**
     * Cache all ancestors when analyzing inheritance
     */
    private final Set<ClassDefinition> ancestors = new HashSet<>();
    /**
     * All interfaces implemented directly or indirectly
     */
    private final Set<InterfaceDefinition> allImpls = new HashSet<>();
    /**
     * All field sets: including {@link #fields} and {@link #inheritFields}
     */
    private IdentifierMap<ClassField> allFields = new IdentifierMap<>();
    /**
     * All inherited field sets
     */
    private IdentifierMap<ClassField> inheritFields = new IdentifierMap<>();
    /**
     * All method sets: including {@link #methods} and {@link #inheritMethods}
     */
    private IdentifierMap<ClassMethod> allMethods = new IdentifierMap<>();
    /**
     * All inherited method sets
     */
    private IdentifierMap<ClassMethod> inheritMethods = new IdentifierMap<>();
    /**
     * Resource class analysis callback：
     * <p>
     * {@code  macro resource free() {}}
     */
    private final Lazy<ClassMethod> resourceFree = Lazy.nil();
    /**
     * Implemented binary operations
     */
    private Map<BinaryOperator, ClassMethod> binaryOperators = new HashMap<>();
    /**
     * Implemented unary operations
     */
    private Map<UnaryOperator, ClassMethod> unaryOperators = new HashMap<>();
    /**
     * Implemented index operation:
     * <p>
     * {@code var v = a[i];}
     * <p>
     * {@code a[i] = v;}
     */
    private final Lazy<IndexOperator> indexOperator = Lazy.nil();
    /**
     * Cache the result of check Sync
     */
    private boolean syncable;

    public List<DerivedType> supers() {
        if (parent.match(p -> p == ClassDefinition.ObjectClass))
            return impl.values();
        return Stream.concat(inherit.stream(), impl.stream()).toList();
    }

    public Lazy<ClassDefinition> parent() {
        return parent;
    }

    public int inherited() {
        return inherited;
    }

    public void markInherited() {
        this.inherited++;
    }

    public Set<ClassDefinition> ancestors() {
        return ancestors;
    }

    public Set<InterfaceDefinition> allImpls() {
        return allImpls;
    }

    public IdentifierMap<ClassField> allFields() {
        return allFields;
    }

    public IdentifierMap<ClassField> inheritFields() {
        return inheritFields;
    }

    public IdentifierMap<ClassMethod> allMethods() {
        return allMethods;
    }

    public Optional<ClassMethod> method(Identifier name) {
        return (allMethods.isEmpty() ? methods : allMethods).tryGet(name);
    }

    public IdentifierMap<ClassMethod> inheritMethods() {
        return inheritMethods;
    }

    public boolean isException() {
        // A class is an exception class if Exception is in its ancestor chain
        if (this == ExceptionClass) return true;
        return parent().match(ClassDefinition::isException);
    }

    public boolean resource() {
        return resourceFree.has() || macros.resourceFree().has();
    }

    public Lazy<ClassMethod> resourceFree() {
        return resourceFree;
    }

    public Map<BinaryOperator, ClassMethod> binaryOperators() {
        return binaryOperators;
    }

    public Map<UnaryOperator, ClassMethod> unaryOperators() {
        return unaryOperators;
    }

    public Lazy<IndexOperator> indexOperator() {
        return indexOperator;
    }

    public boolean syncable() {
        return syncable;
    }

    public void syncable(boolean sync) {
        this.syncable = sync;
    }

    //


    // static

    public static final Symbol ObjectSymbol = new Symbol(new Identifier("Object"));
    public static final DerivedType ObjectType = new DerivedType(
            Position.ZERO, ObjectSymbol, TypeArguments.EMPTY);
    // The root class of all non-final class
    public static final ClassDefinition ObjectClass =
            new ClassDefinition(Position.ZERO, Modifier.empty(),
                    ObjectSymbol, TypeParameters.empty(),
                    Optional.empty(), new SymbolMap<>(),
                    new IdentifierMap<>(), new IdentifierMap<>(),
                    new MacroTable());

    // --- built-in exception classes ---

    /**
     * The built-in Exception class — base of all exception types.
     * The standard library {@code std$error$Exception} overlays this definition.
     */
    public static final Symbol ExceptionSymbol =
            new Symbol(new Identifier("Exception"));

    private static IdentifierMap<ClassField> _exFields() {
        var m = new IdentifierMap<ClassField>(2);
        var fn = new ClassField(Position.ZERO, Modifier.empty(),
                Declare.VAR, new Identifier("fn"),
                Primitive.UINT64.declarer());
        var line = new ClassField(Position.ZERO, Modifier.empty(),
                Declare.VAR, new Identifier("line"),
                Primitive.UINT32.declarer());
        m.add(fn.name(), fn);
        m.add(line.name(), line);
        return m;
    }

    /**
     * The {@code trace(fnAddr uint64, lineNum uint32)} method on Exception.
     */
    private static final ClassMethod traceMethod = new ClassMethod(Position.ZERO,
            Modifier.empty(true), new Identifier("trace"),
            TypeParameters.empty(), false, false,
            new Prototype(Position.ZERO, new ParameterSet(Position.ZERO, List.of(
                    FixedParameter.create("fnAddr", Primitive.UINT64),
                    FixedParameter.create("lineNum", Primitive.UINT32)
            )), Optional.empty()), false);

    public static final ClassDefinition ExceptionClass;
    public static final ClassDefinition NilExceptionClass;
    public static final ClassDefinition OutOfBoundsExceptionClass;
    public static final ClassDefinition AssertExceptionClass;

    static {
        // -- Exception --
        var exFields = _exFields();
        var exMethods = new IdentifierMap<ClassMethod>(1);
        exMethods.add(traceMethod.name(), traceMethod);
        ExceptionClass = new ClassDefinition(Position.ZERO,
                Modifier.empty(), ExceptionSymbol,
                TypeParameters.empty(), false,
                Optional.of(new DerivedType(Position.ZERO,
                        ObjectSymbol, TypeArguments.EMPTY)),
                new SymbolMap<>(), exFields, exMethods, new MacroTable());

        // -- NilException (inherits fn/line fields from Exception) --
        NilExceptionClass = new ClassDefinition(Position.ZERO, Modifier.empty(),
                new Symbol(new Identifier("NilException")),
                TypeParameters.empty(), false,
                Optional.of(new DerivedType(Position.ZERO,
                        ExceptionSymbol, TypeArguments.EMPTY)),
                new SymbolMap<>(), new IdentifierMap<>(),
                new IdentifierMap<>(), new MacroTable());

        // -- OutOfBoundsException (inherits fn/line fields from Exception) --
        OutOfBoundsExceptionClass = new ClassDefinition(Position.ZERO, Modifier.empty(),
                new Symbol(new Identifier("OutOfBoundsException")),
                TypeParameters.empty(), false,
                Optional.of(new DerivedType(Position.ZERO,
                        ExceptionSymbol, TypeArguments.EMPTY)),
                new SymbolMap<>(), new IdentifierMap<>(),
                new IdentifierMap<>(), new MacroTable());

        // -- AssertException (inherits fn/line fields from Exception) --
        AssertExceptionClass = new ClassDefinition(Position.ZERO, Modifier.empty(),
                new Symbol(new Identifier("AssertException")),
                TypeParameters.empty(), false,
                Optional.of(new DerivedType(Position.ZERO,
                        ExceptionSymbol, TypeArguments.EMPTY)),
                new SymbolMap<>(), new IdentifierMap<>(),
                new IdentifierMap<>(), new MacroTable());

        // mark as built-in
        ObjectClass.builtin(true);
        ExceptionClass.builtin(true);
        NilExceptionClass.builtin(true);
        OutOfBoundsExceptionClass.builtin(true);
        AssertExceptionClass.builtin(true);

        // set trace method master (must be done before wire-up)
        traceMethod.master(ExceptionClass);

        // wire up parent chain
        ExceptionClass.parent().set(ObjectClass);
        ObjectClass.markInherited();

        NilExceptionClass.parent().set(ExceptionClass);
        ExceptionClass.markInherited();

        OutOfBoundsExceptionClass.parent().set(ExceptionClass);
        ExceptionClass.markInherited();

        AssertExceptionClass.parent().set(ExceptionClass);
        ExceptionClass.markInherited();

        // When analyzing inheritance, some fields will be filled in,
        // which we manually handle here
        for (var cd : List.of(ExceptionClass, NilExceptionClass,
                OutOfBoundsExceptionClass, AssertExceptionClass)) {
            cd.allFields().addAll(cd.fields());
            cd.allMethods().addAll(cd.methods());
            cd.parent().use(pd -> {
                cd.ancestors().add(pd);
                cd.ancestors().addAll(pd.ancestors());

                for (var pf : pd.allFields()) {
                    if (!cd.fields().exists(pf.name())) {
                        var nf = pf.clone();
                        cd.inheritFields().add(nf.name(), nf);
                        cd.allFields().add(nf.name(), nf);
                    }
                }
                for (var pm : pd.allMethods()) {
                    if (!cd.methods().exists(pm.name())) {
                        cd.inheritMethods().add(pm.name(), pm);
                        cd.allMethods().add(pm.name(), pm);
                    }
                }
            });
        }
    }
}
