package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.lit.BoolLiteral;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.ArrayList;
import java.util.List;

public class IfStatement extends Statement implements Scope {
    private Optional<Statement> init;
    private Expression condition;
    private Statement yes;
    private Optional<Statement> not;

    public IfStatement(Position pos,
                       Optional<Statement> init,
                       Expression condition,
                       Statement yes,
                       Optional<Statement> not) {
        super(pos);
        this.init = init;
        this.condition = condition;
        this.yes = yes;
        this.not = not;
    }

    public Optional<Statement> init() {
        return init;
    }

    public Expression condition() {
        return condition;
    }

    public void condition(Expression condition) {
        this.condition = condition;
    }

    public Statement yes() {
        return yes;
    }

    public Optional<Statement> not() {
        return not;
    }


    private final Lazy<BoolLiteral> cond = Lazy.nil();

    public Lazy<BoolLiteral> cond() {
        return cond;
    }

    //

    private volatile List<Variable> stack = List.of();

    public List<Variable> stack() {
        return stack;
    }

    public void stack(List<Variable> variables) {
        stack = variables;
    }

}
