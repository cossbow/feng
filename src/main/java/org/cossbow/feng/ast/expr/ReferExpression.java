package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.gen.TypeArguments;

public class ReferExpression extends PrimaryExpression {
    private final Identifier name;
    private final TypeArguments generic;

    public ReferExpression(Position pos,
                           Identifier name,
                           TypeArguments generic) {
        super(pos);
        this.name = name;
        this.generic = generic;
    }

    public Identifier name() {
        return name;
    }

    public TypeArguments generic() {
        return generic;
    }
}
