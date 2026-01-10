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
                           Optional<Identifier> name,
                           TypeParameters generic,
                           Optional<DefinedType> parent,
                           SymbolTable<DefinedType> impl,
                           IdentifierTable<ClassField> fields,
                           IdentifierTable<ClassMethod> methods,
                           MacroTable macros) {
        super(pos, modifier, name, generic);
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

    public MacroTable macros() {
        return macros;
    }
}
