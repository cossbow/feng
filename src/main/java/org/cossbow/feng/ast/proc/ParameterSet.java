package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.Variable;
import org.cossbow.feng.util.Groups;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParameterSet implements Iterable<Variable> {
    private IdentifierMap<Variable> variables;

    public ParameterSet(IdentifierMap<Variable> variables) {
        this.variables = variables;
    }

    public ParameterSet(List<Variable> list) {
        this.variables = new IdentifierMap<>(list.stream()
                .map(v -> Groups.g2(v.name(), v)).toList());
    }

    public ParameterSet() {
        variables = new IdentifierMap<>();
    }

    public IdentifierMap<Variable> variables() {
        return variables;
    }

    public List<TypeDeclarer> types() {
        return new PhantomList();
    }

    public Variable getVar(int i) {
        return variables.getValue(i);
    }

    public TypeDeclarer getType(int i) {
        return getVar(i).type().must();
    }

    public int size() {
        return variables.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public Iterator<Variable> iterator() {
        return variables.iterator();
    }

    public Stream<Variable> stream() {
        return variables.stream();
    }

    /**
     * check if this type contains type-paramster:
     * <p>
     * If {@code T} is type-parameter in this context, a type with
     * type-var is like: {@code T}, {@code List`T`}, etc.
     */
    public boolean hasTypeVar() {
        return variables.stream().anyMatch(v ->
                v.type().match(TypeDeclarer::hasTypeVar));
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ParameterSet t)) return false;

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

    //

    @Override
    public String toString() {
        return variables.stream().map(Variable::toString)
                .collect(Collectors.joining(", "));
    }
}
