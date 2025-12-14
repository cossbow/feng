package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;

public class InterfaceDefinition extends TypeDefinition {
    private final IdentifierTable<InterfaceMethod> methods;
    private final SymbolTable<DefinedType> parts;
    private final MacroTable macros;

    public InterfaceDefinition(Position pos,
                               Modifier modifier,
                               Identifier name,
                               TypeParameters generic,
                               IdentifierTable<InterfaceMethod> methods,
                               SymbolTable<DefinedType> parts,
                               MacroTable macros) {
        super(pos, modifier, Optional.of(name), generic);
        this.methods = methods;
        this.parts = parts;
        this.macros = macros;
    }

    public IdentifierTable<InterfaceMethod> methods() {
        return methods;
    }

    public SymbolTable<DefinedType> parts() {
        return parts;
    }

    public MacroTable macros() {
        return macros;
    }
}
