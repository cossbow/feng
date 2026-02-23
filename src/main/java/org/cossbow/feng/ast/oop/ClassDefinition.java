package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ClassDefinition extends ObjectDefinition {
    private Optional<DerivedType> inherit;
    private SymbolTable<DerivedType> impl;
    private IdentifierTable<ClassField> fields;
    private IdentifierTable<ClassMethod> methods;
    private MacroTable macros;

    public ClassDefinition(Position pos,
                           Modifier modifier,
                           Symbol symbol,
                           TypeParameters generic,
                           Optional<DerivedType> inherit,
                           SymbolTable<DerivedType> impl,
                           IdentifierTable<ClassField> fields,
                           IdentifierTable<ClassMethod> methods,
                           MacroTable macros) {
        super(pos, modifier, symbol, generic, TypeDomain.CLASS);
        this.inherit = inherit;
        this.impl = impl;
        this.fields = fields;
        this.methods = methods;
        this.macros = macros;
    }

    public Optional<DerivedType> inherit() {
        return inherit;
    }

    public SymbolTable<DerivedType> impl() {
        return impl;
    }

    public IdentifierTable<ClassField> fields() {
        return fields;
    }

    public IdentifierTable<ClassMethod> methods() {
        return methods;
    }

    public boolean hasMember(Identifier name) {
        return fields.exists(name) || methods.exists(name);
    }

    public MacroTable macros() {
        return macros;
    }

    public Optional<ClassField> field(Identifier name) {
        return fields.tryGet(name);
    }

    //

    private final int id = IdGenerator.getAndIncrement();
    private final Lazy<ClassDefinition> parent = Lazy.nil();
    private final List<ClassDefinition> ancestors = new ArrayList<>();
    private final List<InterfaceDefinition> allImpls = new ArrayList<>();
    private final IdentifierTable<ClassField> allFields = new IdentifierTable<>();
    private final IdentifierTable<ClassMethod> allMethods = new IdentifierTable<>();
    private volatile boolean resource;

    public int id() {
        return id;
    }

    public Lazy<ClassDefinition> parent() {
        return parent;
    }

    public List<ClassDefinition> ancestors() {
        return ancestors;
    }

    public List<InterfaceDefinition> allImpls() {
        return allImpls;
    }

    public IdentifierTable<ClassField> allFields() {
        return allFields;
    }

    public IdentifierTable<ClassMethod> allMethods() {
        return allMethods;
    }

    public boolean resource() {
        return resource;
    }

    public void resource(boolean resource) {
        this.resource = resource;
    }

    //

    // static

    private static final AtomicInteger IdGenerator = new AtomicInteger(0);

    public static int maxId() {
        return IdGenerator.get();
    }

    public static final Identifier ReleaseName = new Identifier(
            Position.ZERO, "release");

    public static final Identifier ObjectName = new Identifier(
            Position.ZERO, "Object");
    public static final Symbol ObjectSymbol = new Symbol(
            Position.ZERO, ObjectName);
    public static final DerivedType ObjectType = new DerivedType(
            Position.ZERO, ObjectSymbol, TypeArguments.EMPTY);

    public static final ClassDefinition ObjectClass =
            new ClassDefinition(Position.ZERO, Modifier.empty(),
                    new Symbol(Position.ZERO, new Identifier(
                            Position.ZERO, "Object")),
                    TypeParameters.empty(), Optional.empty(),
                    new SymbolTable<>(), new IdentifierTable<>(),
                    new IdentifierTable<>(), new MacroTable());

    static {
        ObjectClass.builtin(true);
    }
}
