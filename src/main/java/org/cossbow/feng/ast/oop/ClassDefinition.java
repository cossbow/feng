package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ClassDefinition extends ObjectDefinition {
    private boolean isFinal;
    private Optional<DerivedType> inherit;
    private SymbolMap<DerivedType> impl;
    private IdentifierMap<ClassField> fields;
    private IdentifierMap<ClassMethod> methods;
    private MacroTable macros;

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

    public void fields(IdentifierMap<ClassField> fields) {
        this.fields = fields;
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

    private final int id = IdGenerator.getAndIncrement();
    private final Lazy<ClassDefinition> parent = Lazy.nil();
    private final Set<ClassDefinition> ancestors = new HashSet<>();
    private final Set<InterfaceDefinition> allImpls = new HashSet<>();
    private IdentifierMap<ClassField> allFields = new IdentifierMap<>();
    private IdentifierMap<ClassField> inheritFields = new IdentifierMap<>();
    private IdentifierMap<ClassMethod> allMethods = new IdentifierMap<>();
    private IdentifierMap<ClassMethod> inheritMethods = new IdentifierMap<>();
    private final Lazy<ClassMethod> resourceFree = Lazy.nil();

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

    public static final ClassDefinition ObjectClass =
            new ClassDefinition(Position.ZERO, Modifier.empty(),
                    new Symbol(new Identifier(Position.ZERO, "Object")),
                    TypeParameters.empty(), Optional.empty(),
                    new SymbolMap<>(), new IdentifierMap<>(),
                    new IdentifierMap<>(), new MacroTable());

    static {
        ObjectClass.builtin(true);
    }
}
