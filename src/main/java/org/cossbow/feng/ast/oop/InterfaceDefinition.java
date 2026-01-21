package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.util.Lazy;

public class InterfaceDefinition extends ObjectDefinition {
    private IdentifierTable<InterfaceMethod> methods;
    private SymbolTable<DerivedType> parts;
    private MacroTable macros;

    public InterfaceDefinition(Position pos,
                               Modifier modifier,
                               Symbol symbol,
                               TypeParameters generic,
                               IdentifierTable<InterfaceMethod> methods,
                               SymbolTable<DerivedType> parts,
                               MacroTable macros) {
        super(pos, modifier, symbol, generic, TypeDomain.INTERFACE);
        this.methods = methods;
        this.parts = parts;
        this.macros = macros;
    }

    public IdentifierTable<InterfaceMethod> methods() {
        return methods;
    }

    public SymbolTable<DerivedType> parts() {
        return parts;
    }

    public MacroTable macros() {
        return macros;
    }

    //

    private transient IdentifierTable<InterfaceMethod> all = new IdentifierTable<>();

    public IdentifierTable<InterfaceMethod> all() {
        return all;
    }
}
