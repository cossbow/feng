package org.cossbow.feng.ast.var;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Lazy;

import java.util.ArrayList;
import java.util.List;

abstract
public class Operand extends Entity {
    public Operand(Position pos) {
        super(pos);
    }

    abstract public Expression rhs();

    public final Lazy<TypeDeclarer> type = Lazy.nil();
    private final List<Variable> relay = new ArrayList<>();

    public Lazy<TypeDeclarer> type() {
        return type;
    }

    public List<Variable> relay() {
        return relay;
    }
}
