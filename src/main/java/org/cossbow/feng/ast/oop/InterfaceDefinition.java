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

public class InterfaceDefinition extends TypeDefinition {
    private final List<InterfaceMethod> methods;
    private final List<DefinedType> parts;
    private final List<Macro> macros;

    public InterfaceDefinition(Position pos,
                               Modifier modifier,
                               Identifier name,
                               TypeParameters generic,
                               List<InterfaceMethod> methods,
                               List<DefinedType> parts,
                               List<Macro> macros) {
        super(pos, modifier, Optional.of(name), generic);
        this.methods = methods;
        this.parts = parts;
        this.macros = macros;
    }

    public List<InterfaceMethod> methods() {
        return methods;
    }

    public List<DefinedType> parts() {
        return parts;
    }

    public List<Macro> macros() {
        return macros;
    }
}
