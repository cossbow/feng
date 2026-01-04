package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

public class FunctionDefinition extends Definition {
    private Procedure procedure;

    public FunctionDefinition(Position pos,
                              Modifier modifier,
                              Optional<Identifier> name,
                              TypeParameters generic,
                              Procedure procedure) {
        super(pos, modifier, name, generic);

        this.procedure = procedure;
    }

    public Procedure procedure() {
        return procedure;
    }
}
