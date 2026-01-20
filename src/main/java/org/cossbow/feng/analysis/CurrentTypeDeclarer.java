package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.expr.CurrentExpression;

/**
 * 临时，不在AST上
 */
public class CurrentTypeDeclarer extends TypeDeclarer {
    private final CurrentExpression current;

    public CurrentTypeDeclarer(Position pos,
                               CurrentExpression current) {
        super(pos);
        this.current = current;
    }

    @Override
    public boolean equals(Object obj) {
        return false;
    }
}
