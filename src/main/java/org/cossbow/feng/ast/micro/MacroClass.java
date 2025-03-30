package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.UniqueTable;

public class MacroClass extends Macro {
    private final Identifier name;
    private final UniqueTable<MacroVariable> fields;
    private final UniqueTable<MacroProcedure> methods;

    public MacroClass(Position pos,
                      Identifier type,
                      Identifier name,
                      UniqueTable<MacroVariable> fields,
                      UniqueTable<MacroProcedure> methods) {
        super(pos, type);
        this.name = name;
        this.fields = fields;
        this.methods = methods;
    }

    public Identifier name() {
        return name;
    }

    public UniqueTable<MacroVariable> fields() {
        return fields;
    }

    public UniqueTable<MacroProcedure> methods() {
        return methods;
    }
}
