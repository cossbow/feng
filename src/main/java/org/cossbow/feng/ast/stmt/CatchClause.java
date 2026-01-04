package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.List;

public class CatchClause extends Entity {
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
}
