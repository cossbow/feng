package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

public class SizeofExpression extends PrimaryExpression {

    private final TypeDeclarer type;

    public SizeofExpression(Position pos,
                            TypeDeclarer type) {
        super(pos);
        this.type = type;
    }

    public TypeDeclarer type() {
        return type;
    }

}
