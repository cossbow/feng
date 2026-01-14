package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Optional;

public class NewMemType extends NewType {
    private Optional<TypeDeclarer> mapped;

    public NewMemType(Position pos,
                      Optional<TypeDeclarer> mapped) {
        super(pos);
        this.mapped = mapped;
    }

    public Optional<TypeDeclarer> mapped() {
        return mapped;
    }
}
