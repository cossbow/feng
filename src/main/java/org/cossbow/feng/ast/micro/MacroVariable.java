package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

public class MacroVariable extends Entity {
    private Identifier name;
    private Lazy<TypeDeclarer> type;

    public MacroVariable(Position pos,
                         Identifier name,
                         Lazy<TypeDeclarer> type) {
        super(pos);
        this.name = name;
        this.type = type;
    }

    public Identifier name() {
        return name;
    }

    public Lazy<TypeDeclarer> type() {
        return type;
    }
}
