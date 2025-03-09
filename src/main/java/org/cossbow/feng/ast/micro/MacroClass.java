package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;

import java.util.List;

public class MacroClass extends Macro {
    private final Identifier name;
    private final List<MacroVariable> fields;
    private final List<MacroProcedure> methods;

    public MacroClass(Position pos,
                      Identifier type,
                      Identifier name,
                      List<MacroVariable> fields,
                      List<MacroProcedure> methods) {
        super(pos, type);
        this.name = name;
        this.fields = fields;
        this.methods = methods;
    }

    public Identifier name() {
        return name;
    }

    public List<MacroVariable> fields() {
        return fields;
    }

    public List<MacroProcedure> methods() {
        return methods;
    }
}
