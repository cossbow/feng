package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeArguments;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.List;

public class ClassDefinition extends ObjectDefinition
        implements HaveFields<ClassField> {
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

    //

    private transient Lazy<ClassDefinition> parent = Lazy.nil();
    private transient IdentifierTable<InterfaceDefinition> interfaces = new IdentifierTable<>();
    private transient IdentifierTable<ClassField> allFields = new IdentifierTable<>();
    private transient IdentifierTable<ClassMethod> allMethods = new IdentifierTable<>();

    public Lazy<ClassDefinition> parent() {
        return parent;
    }

    public IdentifierTable<InterfaceDefinition> interfaces() {
        return interfaces;
    }

    public IdentifierTable<ClassField> allFields() {
        return allFields;
    }

    public IdentifierTable<ClassMethod> allMethods() {
        return allMethods;
    }

    //

    private final Lazy<List<ClassDefinition>> initDeps = Lazy.nil();

    public Lazy<List<ClassDefinition>> initDeps() {
        return initDeps;
    }

    // static

    public static final Identifier ObjectName = new Identifier(
            Position.ZERO, "Object");
    public static final Symbol ObjectSymbol = new Symbol(
            Position.ZERO, ObjectName);
    public static final Optional<DerivedType> ObjectType = Optional.of(
            new DerivedType(Position.ZERO, ObjectSymbol, TypeArguments.EMPTY));

    public static final ClassDefinition ObjectClass =
            new ClassDefinition(Position.ZERO, Modifier.empty(),
                    new Symbol(Position.ZERO, new Identifier(
                            Position.ZERO, "Object")),
                    TypeParameters.empty(), Optional.empty(),
                    new SymbolTable<>(), new IdentifierTable<>(),
                    new IdentifierTable<>(), new MacroTable());
}
