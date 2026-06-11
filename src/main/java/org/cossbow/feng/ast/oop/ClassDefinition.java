package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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

    public ClassDefinition(Position pos, Symbol symbol,
                           IdentifierMap<ClassField> fields,
                           IdentifierMap<ClassMethod> methods) {
        this(pos, Modifier.empty(), symbol, TypeParameters.empty(),
                fields, methods, new MacroTable());
    }

    public ClassDefinition(Position pos, Symbol symbol,
                           IdentifierMap<ClassField> fields) {
        this(pos, Modifier.empty(), symbol, TypeParameters.empty(),
                fields, new IdentifierMap<>(), new MacroTable());
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
     * Automatically generate class IDs for implementing dynamic features
     */
    private final int id = IdGenerator.getAndIncrement();
    /**
     * After analyzing the {@link #inherit}, cache will be defined here
     */
    private final Lazy<ClassDefinition> parent = Lazy.nil();
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

    public int id() {
        return id;
    }

    public List<DerivedType> supers() {
        if (parent.match(p -> p == ClassDefinition.ObjectClass))
            return impl.values();
        return Stream.concat(inherit.stream(), impl.stream()).toList();
    }

    public Lazy<ClassDefinition> parent() {
        return parent;
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

    public IdentifierMap<ClassMethod> inheritMethods() {
        return inheritMethods;
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

    //


    // static

    private static final AtomicInteger IdGenerator = new AtomicInteger(0);

    public static int maxId() {
        return IdGenerator.get();
    }

    public static final Identifier ObjectName = new Identifier(
            Position.ZERO, "Object");
    public static final Symbol ObjectSymbol = new Symbol(ObjectName);
    public static final DerivedType ObjectType = new DerivedType(
            Position.ZERO, ObjectSymbol, TypeArguments.EMPTY);
    // The root class of all non-final class
    public static final ClassDefinition ObjectClass =
            new ClassDefinition(Position.ZERO, Modifier.empty(),
                    new Symbol(new Identifier(Position.ZERO, "Object")),
                    TypeParameters.empty(), Optional.empty(),
                    new SymbolMap<>(), new IdentifierMap<>(),
                    new IdentifierMap<>(), new MacroTable());
}
