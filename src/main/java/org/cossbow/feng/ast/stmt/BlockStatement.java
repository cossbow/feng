package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.gen.GenericMap;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.ArrayList;
import java.util.List;

public class BlockStatement extends Statement implements Scope {
    private List<Statement> list;
    private final boolean newScope;

    public BlockStatement(Position pos,
                          List<Statement> list) {
        this(pos, list, true);
    }

    public BlockStatement(Position pos,
                          List<Statement> list,
                          boolean newScope) {
        super(pos);
        this.list = list;
        this.newScope = newScope;
    }

    public List<Statement> list() {
        return list;
    }

    public void list(List<Statement> list) {
        this.list = list;
    }

    public int size() {
        return list.size();
    }

    public boolean isEmpty() {
        return list.isEmpty();
    }

    public Statement get(int i) {
        return list.get(i);
    }

    public Statement last() {
        return list.getLast();
    }

    public boolean newScope() {
        return newScope;
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
    public BlockStatement mirror() {
        var list = new ArrayList<Statement>(this.list.size());
        for (var s : this.list) list.add(s.mirror());
        var n = new BlockStatement(pos(), list, newScope);
        n.stack = List.of();
        return n;
    }

    @Override
    public BlockStatement mono(GenericMap gm) {
        var list = new ArrayList<Statement>(this.list.size());
        for (var s : this.list) list.add(s.mono(gm));
        return new BlockStatement(pos(), list, newScope);
    }
}
