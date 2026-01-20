package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DefinedType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.util.Lazy;

public class InterfaceDefinition extends ObjectDefinition {
    private IdentifierTable<InterfaceMethod> methods;
    private SymbolTable<DefinedType> parts;
    private MacroTable macros;

    public InterfaceDefinition(Position pos,
                               Modifier modifier,
                               Symbol symbol,
                               TypeParameters generic,
                               IdentifierTable<InterfaceMethod> methods,
                               SymbolTable<DefinedType> parts,
                               MacroTable macros) {
        super(pos, modifier, symbol, generic, TypeDomain.INTERFACE);
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

    //

    private transient Lazy<IdentifierTable<InterfaceMethod>> all = Lazy.nil();

    public Lazy<IdentifierTable<InterfaceMethod>> all() {
        return all;
    }
}
