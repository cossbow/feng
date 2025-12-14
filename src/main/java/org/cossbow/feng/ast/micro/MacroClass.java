package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.IdentifierTable;

public class MacroClass extends Macro {
    private final Identifier name;
    private final IdentifierTable<MacroVariable> fields;
    private final IdentifierTable<MacroProcedure> methods;

    public MacroClass(Position pos,
                      Identifier type,
                      Identifier name,
                      IdentifierTable<MacroVariable> fields,
                      IdentifierTable<MacroProcedure> methods) {
        super(pos, type);
        this.name = name;
        this.fields = fields;
        this.methods = methods;
    }

    public Identifier name() {
        return name;
    }

    public IdentifierTable<MacroVariable> fields() {
        return fields;
    }

    public IdentifierTable<MacroProcedure> methods() {
        return methods;
    }
}
