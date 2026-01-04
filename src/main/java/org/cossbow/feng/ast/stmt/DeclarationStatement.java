package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.List;

public class DeclarationStatement extends Statement {
    private List<Variable> variables;

    public DeclarationStatement(Position pos,
                                List<Variable> variables) {
        super(pos);
        this.variables = variables;
    }

    public List<Variable> variables() {
        return variables;
    }

    public int size() {
        return variables.size();
    }
}
