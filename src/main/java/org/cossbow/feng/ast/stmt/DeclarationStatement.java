package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.List;

public class DeclarationStatement extends Statement {
    private List<Variable> variables;
    private Optional<Tuple> init;

    public DeclarationStatement(Position pos,
                                List<Variable> variables,
                                Optional<Tuple> init) {
        super(pos);
        this.variables = variables;
        this.init = init;
    }

    public List<Variable> variables() {
        return variables;
    }

    public Optional<Tuple> init() {
        return init;
    }
}
