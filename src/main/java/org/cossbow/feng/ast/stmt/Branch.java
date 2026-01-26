package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.ArrayList;
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

    @Override
    public void stack(List<Variable> variables) {
        stack = variables;
    }
}
