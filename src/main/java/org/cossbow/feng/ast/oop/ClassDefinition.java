package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

public class ClassDefinition extends TypeDefinition
        implements HaveFields<ClassField> {
    private Optional<DefinedType> inherit;
    private SymbolTable<DefinedType> impl;
    private IdentifierTable<ClassField> fields;
    private IdentifierTable<ClassMethod> methods;
    private MacroTable macros;

    public ClassDefinition(Position pos,
                           Modifier modifier,
                           Symbol symbol,
                           TypeParameters generic,
                           Optional<DefinedType> inherit,
                           SymbolTable<DefinedType> impl,
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

    public Optional<DefinedType> inherit() {
        return inherit;
    }

    public SymbolTable<DefinedType> impl() {
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

    public Lazy<ClassDefinition> parent() {
        return parent;
    }

    //

    public static final Identifier ObjectName = new Identifier(Position.ZERO, "Object");
    public static final Symbol ObjectSymbol = new Symbol(Position.ZERO, ObjectName);

    public static final ClassDefinition ObjectClass =
            new ClassDefinition(Position.ZERO, Modifier.empty(),
                    new Symbol(Position.ZERO, new Identifier(
                            Position.ZERO, "Object")),
                    TypeParameters.empty(), Optional.empty(),
                    new SymbolTable<>(), new IdentifierTable<>(),
                    new IdentifierTable<>(), new MacroTable());
}
