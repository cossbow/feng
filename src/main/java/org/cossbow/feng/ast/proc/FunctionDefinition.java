package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Definition;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.gen.TypeParameters;

public class FunctionDefinition extends Definition {
    private Procedure procedure;

    public FunctionDefinition(Position pos,
                              Modifier modifier,
                              Identifier name,
                              TypeParameters generic,
                              Procedure procedure) {
        super(pos, modifier, name, generic);

        this.procedure = procedure;
    }

    public Procedure procedure() {
        return procedure;
    }
}
