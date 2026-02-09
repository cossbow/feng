package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;

import java.util.ArrayList;
import java.util.List;

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

    public final List<InterfaceDefinition> deps = new ArrayList<>();
    public final List<ClassDefinition> impls = new ArrayList<>();
    public final IdentifierTable<InterfaceMethod> allMethods = new IdentifierTable<>();

}
