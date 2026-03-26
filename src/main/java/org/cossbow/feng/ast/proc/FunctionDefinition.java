package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Definition;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.List;

import static org.cossbow.feng.ast.Position.ZERO;

public class FunctionDefinition extends Definition {
    private Prototype prototype;
    private Optional<Procedure> procedure;

    private FunctionDefinition(Position pos,
                               Modifier modifier,
                               Symbol symbol,
                               TypeParameters generic,
                               Prototype prototype,
                               Optional<Procedure> procedure) {
        super(pos, modifier, symbol, generic);
        this.prototype = prototype;
        this.procedure = procedure;
    }

    public FunctionDefinition(Position pos,
                              Modifier modifier,
                              Symbol symbol,
                              TypeParameters generic,
                              Procedure procedure) {
        this(pos, modifier, symbol, generic,
                procedure.prototype(),
                Optional.of(procedure));
    }

    public FunctionDefinition(Position pos,
                              Modifier modifier,
                              Symbol symbol,
                              TypeParameters generic,
                              Prototype prototype) {
        this(pos, modifier, symbol, generic,
                prototype, Optional.empty());
    }

    public Procedure procedure() {
        return procedure.must();
    }

    public Prototype prototype() {
        return prototype;
    }

    private boolean entry;

    public boolean entry() {
        return entry;
    }

    public void entry(boolean entry) {
        this.entry = entry;
    }

    //

    public static final Identifier MAIN_ID = new Identifier("main");
    public static final Symbol MAIN_SYMBOL = new Symbol(MAIN_ID);
    public static final FunctionDefinition MAIN_FUNC;

    static {
        var et = new ArrayTypeDeclarer(ZERO, Primitive.BYTE.declarer(ZERO),
                Optional.empty(), Optional.of(new Refer(
                ZERO, ReferKind.STRONG, true, true)));
        var at = new ArrayTypeDeclarer(ZERO, et, Optional.empty(), Optional.of(
                new Refer(ZERO, ReferKind.PHANTOM, true, true)));
        var arg = new Variable(ZERO, Modifier.empty(), Declare.CONST,
                new Identifier("Feng$param"), Lazy.of(at), Lazy.nil());
        var args = new ParameterSet(List.of(arg));
        var prot = new Prototype(ZERO, args, Optional.empty());
        MAIN_FUNC = new FunctionDefinition(
                ZERO, Modifier.empty(), MAIN_SYMBOL, TypeParameters.empty(),
                prot, Optional.empty());
    }

    //
    @Override
    public String toString() {
        return "func " + symbol() + procedure;
    }
}
