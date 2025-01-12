package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Lazy;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

public class MacroVariable extends Macro {
    private final Lazy<TypeDeclarer> type;

    public MacroVariable(Position pos,
                         Identifier name,
                         Lazy<TypeDeclarer> type) {
        super(pos, name);
        this.type = type;
    }

    public Lazy<TypeDeclarer> type() {
        return type;
    }
}
