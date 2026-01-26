package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Lazy;

abstract
public class Operand extends Entity {
    public Operand(Position pos) {
        super(pos);
    }

    abstract public Expression rhs();

    public final Lazy<TypeDeclarer> type = Lazy.nil();

}
