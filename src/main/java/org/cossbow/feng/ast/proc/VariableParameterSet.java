package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.UniqueTable;
import org.cossbow.feng.ast.dcl.Variable;

public class VariableParameterSet extends ParameterSet {
    private final UniqueTable<Variable> variables;

    public VariableParameterSet(UniqueTable<Variable> variables) {
        this.variables = variables;
    }

    public UniqueTable<Variable> variables() {
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
