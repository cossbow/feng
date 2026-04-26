package org.cossbow.feng.ast.micro;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Optional;

public class MacroVariable extends Entity {
    private Identifier name;
    private Optional<TypeDeclarer> type;

    public MacroVariable(Position pos,
                         Identifier name,
                         Optional<TypeDeclarer> type) {
        super(pos);
        this.name = name;
        this.type = type;
    }

    public Identifier name() {
        return name;
    }

    public Optional<TypeDeclarer> type() {
        return type;
    }
}
