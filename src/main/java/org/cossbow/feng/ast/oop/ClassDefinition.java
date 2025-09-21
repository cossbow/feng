package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.Macro;

public class ClassDefinition extends TypeDefinition {
    private final Optional<DefinedType> parent;
    private final UniqueTable<DefinedType> impl;
    private final UniqueTable<ClassField> fields;
    private final UniqueTable<ClassMethod> methods;
    private final MultiTable<Macro> macros;

    public ClassDefinition(Position pos,
                           Modifier modifier,
                           Optional<Identifier> name,
                           TypeParameters generic,
                           Optional<DefinedType> parent,
                           UniqueTable<DefinedType> impl,
                           UniqueTable<ClassField> fields,
                           UniqueTable<ClassMethod> methods,
                           MultiTable<Macro> macros) {
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

    public UniqueTable<DefinedType> impl() {
        return impl;
    }

    public UniqueTable<ClassField> fields() {
        return fields;
    }

    public UniqueTable<ClassMethod> methods() {
        return methods;
    }

    public MultiTable<Macro> macros() {
        return macros;
    }
}
