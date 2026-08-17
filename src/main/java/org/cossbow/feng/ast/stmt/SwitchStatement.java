package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Optional;

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

    public void init(Optional<Statement> init) {
        this.init = init;
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

    public void stack(List<Variable> variables) {
        stack = variables;
    }

    @Override
    public SwitchStatement mirror() {
        var init = this.init.map(Statement::mirror);
        var branches = new ArrayList<SwitchBranch>(this.branches.size());
        for (var b : this.branches) branches.add(b.mirror());
        var def = this.defaultBranch.map(Branch::mirror);
        return new SwitchStatement(pos(), init, value.mirror(), branches, def);
    }

    @Override
    public SwitchStatement mono(GenericMap gm) {
        var init = this.init.map(s -> s.mono(gm));
        var branches = new ArrayList<SwitchBranch>(this.branches.size());
        for (var b : this.branches) branches.add(b.mono(gm));
        var def = this.defaultBranch.map(b -> b.mono(gm));
        return new SwitchStatement(pos(), init, value.mono(gm), branches, def);
    }

}
