package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.util.Optional;

public class ClassDefinition extends TypeDefinition {
    private Optional<DefinedType> parent;
    private SymbolTable<DefinedType> impl;
    private IdentifierTable<ClassField> fields;
    private IdentifierTable<ClassMethod> methods;
    private MacroTable macros;

    public ClassDefinition(Position pos,
                           Modifier modifier,
                           Identifier name,
                           TypeParameters generic,
                           Optional<DefinedType> parent,
                           SymbolTable<DefinedType> impl,
                           IdentifierTable<ClassField> fields,
                           IdentifierTable<ClassMethod> methods,
                           MacroTable macros) {
        super(pos, modifier, name, generic, TypeDomain.CLASS);
        this.parent = parent;
        this.impl = impl;
        this.fields = fields;
        this.methods = methods;
        this.macros = macros;
    }

    public Optional<DefinedType> parent() {
        return parent;
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

    public static final ClassDefinition Object =
            new ClassDefinition(Position.ZERO, Modifier.empty(),
                    new Identifier(Position.ZERO, "Object"),
                    TypeParameters.empty(), Optional.empty(),
                    new SymbolTable<>(), new IdentifierTable<>(),
                    new IdentifierTable<>(), new MacroTable());
}
