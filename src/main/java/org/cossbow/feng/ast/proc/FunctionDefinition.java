package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Definition;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

public class FunctionDefinition extends Definition {
    private final Procedure procedure;
    private final boolean builtin;

    public FunctionDefinition(Position pos,
                              Modifier modifier,
                              Symbol symbol,
                              TypeParameters generic,
                              Procedure procedure,
                              boolean builtin) {
        super(pos, modifier, symbol, generic);

        this.procedure = procedure;
        this.builtin = builtin;
    }

    public FunctionDefinition(Position pos,
                              Modifier modifier,
                              Symbol symbol,
                              TypeParameters generic,
                              Procedure procedure) {
        this(pos, modifier, symbol, generic,
                procedure, false);
    }

    public Procedure procedure() {
        return procedure;
    }

    public Prototype prototype() {
        return procedure.prototype();
    }

    public boolean builtin() {
        return builtin;
    }

    //


    @Override
    public String toString() {
        return "func " + symbol() + procedure;
    }
}
