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
    /**
     * Methods declaration
     */
    private IdentifierMap<InterfaceMethod> methods;
    /**
     * The interface supports combination mode, where
     * other interfaces are referenced as components.
     */
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

    /**
     * Automatically generate class IDs for implementing dynamic features
     */
    private final int id = IdGenerator.getAndIncrement();
    /**
     * Collection of cached interface definitions after analyzing {@link #parts}
     */
    public final List<InterfaceDefinition> partDefs = new ArrayList<>();
    /**
     * All method declarations: including method declarations
     * inherited from composite interfaces
     */
    private final IdentifierMap<InterfaceMethod> allMethods = new IdentifierMap<>();
    /**
     * The collection of classes that implement this interface
     */
    public final Set<ClassDefinition> impls = new HashSet<>();

    public int id() {
        return id;
    }

    public List<DerivedType> supers() {
        return parts.values();
    }

    public void visitParts(Consumer<InterfaceDefinition> user) {
        for (var d : partDefs) {
            user.accept(d);
            d.visitParts(user);
        }
    }

    public IdentifierMap<InterfaceMethod> allMethods() {
        return allMethods;
    }

    //
    private static final AtomicInteger IdGenerator = new AtomicInteger(0);
}
