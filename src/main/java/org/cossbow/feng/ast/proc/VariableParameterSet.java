package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.dcl.Variable;

public class VariableParameterSet extends ParameterSet {
    private IdentifierTable<Variable> variables;

    public VariableParameterSet(IdentifierTable<Variable> variables) {
        this.variables = variables;
    }

    public IdentifierTable<Variable> variables() {
        return variables;
    }

    @Override
    public int size() {
        return variables.size();
    }

    public Variable get(Identifier name) {
        return variables.get(name);
    }


}
