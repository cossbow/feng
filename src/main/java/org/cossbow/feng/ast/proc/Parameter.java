package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.Optional;

public class Parameter extends Entity {
    private final Optional<Variable> variable;
    private final TypeDeclarer type;

    public Parameter(Position pos,
                     Optional<Variable> variable,
                     TypeDeclarer type) {
        super(pos);
        this.variable = variable;
        this.type = type;
    }

    public Optional<Variable> variable() {
        return variable;
    }

    public TypeDeclarer type() {
        return type;
    }

    public boolean unnamed() {
        return variable().isEmpty();
    }
}
