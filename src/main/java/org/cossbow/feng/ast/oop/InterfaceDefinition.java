package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.DerivedTypeDeclarer;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.ast.dcl.Refer;
import org.cossbow.feng.ast.gen.DerivedType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.ast.micro.MacroTable;
import org.cossbow.feng.ast.proc.FixedParameter;
import org.cossbow.feng.ast.proc.ParameterSet;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.Optional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.cossbow.feng.ast.Position.ZERO;
import static org.cossbow.feng.ast.dcl.ReferKind.PHANTOM;

public class InterfaceDefinition extends ObjectDefinition {
    /**
     * Methods declaration
     */
    private final IdentifierMap<InterfaceMethod> methods;
    /**
     * The interface supports combination mode, where
     * other interfaces are referenced as components.
     */
    private final SymbolMap<DerivedType> parts;
    /**
     * [imcompleted]
     */
    private final MacroTable macros;

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

    public Optional<InterfaceMethod> method(Identifier name) {
        return (allMethods.isEmpty() ? methods : allMethods).tryGet(name);
    }

    //
    private static final AtomicInteger IdGenerator = new AtomicInteger(0);

    /**
     * The bytes writer interface
     */
    public static final InterfaceDefinition WriterType;

    static {
        var methods = new IdentifierMap<InterfaceMethod>(2);
        var write = new InterfaceMethod(ZERO, new Identifier("write"),
                false, new Prototype(ZERO,
                new ParameterSet(ZERO, List.of(new FixedParameter(ZERO,
                        StringLiteral.array(ZERO, PHANTOM))))));
        methods.add(write.name(), write);
        WriterType = new InterfaceDefinition(ZERO, Modifier.empty(),
                new Symbol(new Identifier("Writer")),
                TypeParameters.empty(), methods, new SymbolMap<>(),
                new MacroTable());
        WriterType.builtin(true);
    }

    /**
     * The object can write to bytes
     */
    public static final InterfaceDefinition WritableType;

    static {
        var methods = new IdentifierMap<InterfaceMethod>(2);
        var td = new DerivedTypeDeclarer(ZERO, WriterType.link(),
                new Refer(ZERO, PHANTOM, true, false));
        var write = new InterfaceMethod(ZERO, new Identifier("write"),
                true, new Prototype(ZERO,
                new ParameterSet(ZERO, List.of(new FixedParameter(ZERO, td))),
                Primitive.INT.declarer(ZERO)));
        methods.add(write.name(), write);
        WritableType = new InterfaceDefinition(ZERO, Modifier.empty(),
                new Symbol(new Identifier("Writable")),
                TypeParameters.empty(), methods, new SymbolMap<>(),
                new MacroTable());
        WritableType.builtin(true);
    }

}
