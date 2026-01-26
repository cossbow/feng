package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.ArrayList;
import java.util.List;

public class SwitchStatement extends Statement implements Scope {
    private Optional<Statement> init;
    private Expression value;
    private List<SwitchBranch> branches;
    private Optional<Branch> defaultBranch;

    public SwitchStatement(Position pos,
                           Optional<Statement> init,
                           Expression value,
                           List<SwitchBranch> branches,
                           Optional<Branch> defaultBranch) {
        super(pos);
        this.init = init;
        this.value = value;
        this.branches = branches;
        this.defaultBranch = defaultBranch;
    }

    public Optional<Statement> init() {
        return init;
    }

    public Expression value() {
        return value;
    }

    public void value(Expression value) {
        this.value = value;
    }

    public List<SwitchBranch> branches() {
        return branches;
    }

    public Optional<Branch> defaultBranch() {
        return defaultBranch;
    }

    //

    private volatile List<Variable> stack = List.of();

    public List<Variable> stack() {
        return stack;
    }

    @Override
    public void stack(List<Variable> variables) {
        stack = variables;
    }

}
