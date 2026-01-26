package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.ast.expr.Expression;

import java.util.List;

public class DeclarationStatement extends Statement {
    private List<Variable> variables;
    private List<Expression> init;

    public DeclarationStatement(Position pos,
                                List<Variable> variables,
                                List<Expression> init) {
        super(pos);
        this.variables = variables;
        this.init = init;
    }

    public List<Variable> variables() {
        return variables;
    }

    public List<Expression> init() {
        return init;
    }

    public int size() {
        return variables.size();
    }
}
