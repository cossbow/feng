package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.AbstractList;
import java.util.List;

public class VariableParameterSet extends ParameterSet {
    private IdentifierTable<Variable> variables;

    public VariableParameterSet(IdentifierTable<Variable> variables) {
        this.variables = variables;
    }

    public IdentifierTable<Variable> variables() {
        return variables;
    }

    @Override
    public List<TypeDeclarer> types() {
        return new PhantomList();
    }

    @Override
    public TypeDeclarer getType(int i) {
        return variables.getValue(i).type().must();
    }

    @Override
    public int size() {
        return variables.size();
    }

    public Variable get(Identifier name) {
        return variables.get(name);
    }


    class PhantomList extends AbstractList<TypeDeclarer> {

        @Override
        public TypeDeclarer get(int index) {
            return variables.getValue(index).type().must();
        }

        @Override
        public int size() {
            return variables.size();
        }
    }
}
