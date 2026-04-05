package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Definition;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.Optional;

public class FunctionDefinition extends Definition {
    private Prototype prototype;
    private Optional<Procedure> procedure;
    private boolean entry;

    private FunctionDefinition(Position pos,
                               Modifier modifier,
                               Symbol symbol,
                               TypeParameters generic,
                               Prototype prototype,
                               Optional<Procedure> procedure) {
        super(pos, modifier, checkEntry(symbol), generic);
        this.prototype = prototype;
        this.procedure = procedure;
        entry = MAIN_ID.equals(symbol().name());
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

    private static Symbol checkEntry(Symbol s) {
        if (!MAIN_ID.equals(s.name())) return s;
        return new Symbol(s.name());
    }

    public Procedure procedure() {
        return procedure.must();
    }

    public Prototype prototype() {
        return prototype;
    }


    public boolean entry() {
        return entry;
    }

    //

    public static final Identifier MAIN_ID = new Identifier("main");


    //
    @Override
    public String toString() {
        return "func " + symbol() + procedure;
    }
}
