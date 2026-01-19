package org.cossbow.feng.analysis;

import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;


public class VariableTypeDeclarer extends TypeDeclarer {
    private final Variable variable;
    private TypeDeclarer type;

    public VariableTypeDeclarer(Variable variable,
                                TypeDeclarer type) {
        super(variable.pos());
        this.variable = variable;
        this.type = type;
    }

    public Variable variable() {
        return variable;
    }

    public TypeDeclarer type() {
        return type;
    }

    @Override
    public boolean equals(Object obj) {
        return false;
    }
}
