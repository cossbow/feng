package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Definition;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Symbol;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

public class FunctionDefinition extends Definition {
    private Procedure procedure;

    public FunctionDefinition(Position pos,
                              Modifier modifier,
                              Symbol symbol,
                              TypeParameters generic,
                              Procedure procedure) {
        super(pos, modifier, symbol, generic);

        this.procedure = procedure;
    }

    public Procedure procedure() {
        return procedure;
    }

    public Prototype prototype() {
        return procedure.prototype();
    }
}
