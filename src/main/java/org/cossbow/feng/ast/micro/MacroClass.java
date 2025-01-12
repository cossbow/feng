package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

import java.util.List;

public class MacroClass extends Macro {
    private final List<MacroVariable> fields;
    private final List<MacroProcedure> methods;

    public MacroClass(Position pos,
                      Identifier name,
                      List<MacroVariable> fields,
                      List<MacroProcedure> methods) {
        super(pos, name);
        this.fields = fields;
        this.methods = methods;
    }

    public List<MacroVariable> fields() {
        return fields;
    }

    public List<MacroProcedure> methods() {
        return methods;
    }
}
