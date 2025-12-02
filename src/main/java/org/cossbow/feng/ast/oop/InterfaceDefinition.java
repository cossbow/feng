package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.Macro;

import java.util.List;

public class InterfaceDefinition extends TypeDefinition {
    private final UniqueTable<InterfaceMethod> methods;
    private final List<DefinedType> parts;
    private final MultiTable<Macro> macros;

    public InterfaceDefinition(Position pos,
                               Modifier modifier,
                               Identifier name,
                               TypeParameters generic,
                               UniqueTable<InterfaceMethod> methods,
                               List<DefinedType> parts,
                               MultiTable<Macro> macros) {
        super(pos, modifier, Optional.of(name), generic);
        this.methods = methods;
        this.parts = parts;
        this.macros = macros;
    }

    public UniqueTable<InterfaceMethod> methods() {
        return methods;
    }

    public List<DefinedType> parts() {
        return parts;
    }

    public MultiTable<Macro> macros() {
        return macros;
    }
}
