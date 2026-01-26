package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.ArrayList;
import java.util.List;

abstract
public class ForStatement extends Statement implements Scope {
    private Statement body;

    public ForStatement(Position pos, Statement body) {
        super(pos);
        this.body = body;
    }

    public Statement body() {
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
