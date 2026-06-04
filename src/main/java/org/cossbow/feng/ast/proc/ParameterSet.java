package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.ErrorUtil;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class ParameterSet extends Entity
        implements Iterable<Parameter> {
    private final List<Parameter> params;
    private final boolean variadic;

    public ParameterSet(Position pos,
                        List<Parameter> params) {
        super(pos);
        this.params = params;
        variadic = !params.isEmpty() &&
                params.getLast() instanceof VariadicParameter;
    }

    public ParameterSet(Position pos) {
        this(pos, List.of());
    }

    public static ParameterSet anon(List<TypeDeclarer> types) {
        if (types.isEmpty())
            return new ParameterSet(Position.ZERO);

        var params = new ArrayList<Parameter>();
        for (var td : types) {
            var param = new FixedParameter(td.pos(), td);
            params.add(param);
        }
        return new ParameterSet(types.getFirst().pos(), params);
    }

    public List<Parameter> params() {
        return params;
    }

    public List<TypeDeclarer> types() {
        return new PhantomList();
    }

    public Parameter get(int i) {
        return params.get(i);
    }

    public FixedParameter fixed(int i) {
        return (FixedParameter) get(i);
    }

    public int size() {
        return params.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public Iterator<Parameter> iterator() {
        return params.iterator();
    }

    public boolean variadic() {
        return variadic;
    }

    /**
     * check if this type contains type-parameter:
     * <p>
     * If {@code T} is type-parameter in this context, a type with
     * type-var is like: {@code T}, {@code List`T`}, etc.
     */
    public boolean hasTypeVar() {
        for (var p : params) {
            if (p instanceof VariadicParameter)
                return ErrorUtil.unreachable();
            var fp = (FixedParameter) p;
            if (fp.type().hasTypeVar())
                return true;
        }
        return false;
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
            return fixed(index).type();
        }

        @Override
        public int size() {
            return params.size();
        }
    }

    //

    @Override
    public String toString() {
        return params.stream().map(Parameter::toString)
                .collect(Collectors.joining(", "));
    }
}
