package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.Scope;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.ArrayList;
import java.util.List;

public class CatchClause extends Statement implements Scope {
    private Variable argument;
    private List<TypeDeclarer> typeSet;
    private BlockStatement body;

    public CatchClause(Position pos,
                       Variable argument,
                       List<TypeDeclarer> typeSet,
                       BlockStatement body) {
        super(pos);
        this.argument = argument;
        this.typeSet = typeSet;
        this.body = body;
    }

    public Variable argument() {
        return argument;
    }

    public List<TypeDeclarer> typeSet() {
        return typeSet;
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
