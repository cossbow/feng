package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.stmt.BlockStatement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Procedure extends Entity implements Scope {
    private final Prototype prototype;
    private final BlockStatement body;
    private final Set<Identifier> labels;

    public Procedure(Position pos,
                     Prototype prototype,
                     BlockStatement body,
                     Set<Identifier> labels) {
        super(pos);
        this.prototype = prototype;
        this.body = body;
        this.labels = labels;
    }

    public Prototype prototype() {
        return prototype;
    }

    public BlockStatement body() {
        return body;
    }

    public Set<Identifier> labels() {
        return labels;
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


    //
    @Override
    public String toString() {
        return prototype.toString();
    }
}
