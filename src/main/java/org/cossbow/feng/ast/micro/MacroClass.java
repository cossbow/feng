package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.attr.Modifier;

public class MacroClass extends Macro {
    private Identifier name;
    private IdentifierMap<MacroVariable> fields;
    private IdentifierMap<MacroProcedure> methods;

    public MacroClass(Position pos,
                      Modifier modifier,
                      Identifier type,
                      Identifier name,
                      IdentifierMap<MacroVariable> fields,
                      IdentifierMap<MacroProcedure> methods) {
        super(pos, modifier, type);
        this.name = name;
        this.fields = fields;
        this.methods = methods;
    }

    public Identifier name() {
        return name;
    }

    public IdentifierMap<MacroVariable> fields() {
        return fields;
    }

    public IdentifierMap<MacroProcedure> methods() {
        return methods;
    }
}
