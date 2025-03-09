package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

public class MacroFunc extends Macro {
    private final MacroProcedure procedure;

    public MacroFunc(Position pos,
                     Identifier type,
                     MacroProcedure procedure) {
        super(pos, type);
        this.procedure = procedure;
    }

    public MacroProcedure procedure() {
        return procedure;
    }
}
