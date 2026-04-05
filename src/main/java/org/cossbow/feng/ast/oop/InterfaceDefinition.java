package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.micro.MacroTable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class InterfaceDefinition extends ObjectDefinition {
    private IdentifierMap<InterfaceMethod> methods;
    private SymbolMap<DerivedType> parts;
    private MacroTable macros;

    public InterfaceDefinition(Position pos,
                               Modifier modifier,
                               Symbol symbol,
                               TypeParameters generic,
                               IdentifierMap<InterfaceMethod> methods,
                               SymbolMap<DerivedType> parts,
                               MacroTable macros) {
        super(pos, modifier, symbol, generic, TypeDomain.INTERFACE);
        this.methods = methods;
        this.parts = parts;
        this.macros = macros;
    }

    public IdentifierMap<InterfaceMethod> methods() {
        return methods;
    }

    public SymbolMap<DerivedType> parts() {
        return parts;
    }

    public MacroTable macros() {
        return macros;
    }

    //

    private final int id = IdGenerator.getAndIncrement();
    public final List<InterfaceDefinition> partDefs = new ArrayList<>();
    public final IdentifierMap<InterfaceMethod> allMethods = new IdentifierMap<>();
    public final Set<ClassDefinition> impls = new HashSet<>();

    public int id() {
        return id;
    }

    public void visitParts(Consumer<InterfaceDefinition> user) {
        for (var d : partDefs) {
            user.accept(d);
            d.visitParts(user);
        }
    }

    //
    private static final AtomicInteger IdGenerator = new AtomicInteger(0);
}
