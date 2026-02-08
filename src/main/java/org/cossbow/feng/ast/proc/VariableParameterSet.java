package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierTable;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;

final
public class VariableParameterSet {
    private IdentifierTable<Variable> variables;

    public VariableParameterSet(IdentifierTable<Variable> variables) {
        this.variables = variables;
    }

    public IdentifierTable<Variable> variables() {
        return variables;
    }

    public List<TypeDeclarer> types() {
        return new PhantomList();
    }

    public TypeDeclarer getType(int i) {
        return variables.getValue(i).type().must();
    }

    public Identifier getName(int i) {
        return variables.getValue(i).name();
    }

    public int size() {
        return variables.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public Variable get(Identifier name) {
        return variables.get(name);
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VariableParameterSet t)) return false;

        return types().equals(t.types());
    }

    @Override
    public int hashCode() {
        return types().hashCode();
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
