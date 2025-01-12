package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.Macro;

import java.util.List;
import java.util.Optional;

public class ClassDefinition extends TypeDefinition {
    private final Optional<DefinedType> parent;
    private final List<DefinedType> impls;
    private final List<ClassField> fields;
    private final List<ClassMethod> methods;
    private final List<Macro> macros;

    public ClassDefinition(Position pos,
                           Modifier modifier,
                           Optional<Identifier> name,
                           TypeParameters generic,
                           Optional<DefinedType> parent,
                           List<DefinedType> impls,
                           List<ClassField> fields,
                           List<ClassMethod> methods,
                           List<Macro> macros) {
        super(pos, modifier, name, generic);
        this.parent = parent;
        this.impls = impls;
        this.fields = fields;
        this.methods = methods;
        this.macros = macros;
    }

    public Optional<DefinedType> parent() {
        return parent;
    }

    public List<DefinedType> impls() {
        return impls;
    }

    public List<ClassField> fields() {
        return fields;
    }

    public List<ClassMethod> methods() {
        return methods;
    }

    public List<Macro> macros() {
        return macros;
    }
}
