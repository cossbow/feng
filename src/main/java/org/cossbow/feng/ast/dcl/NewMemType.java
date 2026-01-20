package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Optional;

public class NewMemType extends NewType {
    private Optional<TypeDeclarer> mapped;
    private Optional<Expression> length;

    public NewMemType(Position pos,
                      Optional<TypeDeclarer> mapped,
                      Optional<Expression> length) {
        super(pos);
        this.mapped = mapped;
        this.length = length;
    }

    public Optional<TypeDeclarer> mapped() {
        return mapped;
    }

    public Optional<Expression> length() {
        return length;
    }
}
