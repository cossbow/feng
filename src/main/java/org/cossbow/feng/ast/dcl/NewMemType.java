package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.MemType;
import org.cossbow.feng.util.Optional;

public class NewMemType extends NewType {
    private final MemType type;

    public NewMemType(Position pos,
                      MemType type) {
        super(pos);
        this.type = type;
    }

    public MemType type() {
        return type;
    }

    public boolean readonly() {
        return type.readonly();
    }

    public Optional<TypeDeclarer> mapped() {
        return type.mapped();
    }

}
