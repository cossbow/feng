package org.cossbow.feng.ast.stmt;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.List;
import java.util.Optional;

public class DeclarationStatement extends Statement {
    private final List<Variable> variables;
    private final Optional<Tuple> init;

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
