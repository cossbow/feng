package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.attr.Modifier;

public class MacroFunc extends Macro {
    private MacroProcedure procedure;

    public MacroFunc(Position pos,
                     Modifier modifier,
                     Identifier type,
                     MacroProcedure procedure) {
        super(pos, modifier, type);
        this.procedure = procedure;
    }

    public Identifier name() {
        return procedure.name();
    }

    public MacroProcedure procedure() {
        return procedure;
    }
}
