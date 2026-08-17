package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.List;

public class Branch extends Statement implements Scope {
    private BlockStatement body;

    public Branch(Position pos,
                  BlockStatement body) {
        super(pos);
        this.body = body;
    }

    public BlockStatement body() {
        return body;
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
    public Branch mirror() {
        var n = new Branch(pos(), body.mirror());
        n.stack = List.of();
        return n;
    }

    @Override
    public Branch mono(GenericMap gm) {
        return new Branch(pos(), body.mono(gm));
    }
}
