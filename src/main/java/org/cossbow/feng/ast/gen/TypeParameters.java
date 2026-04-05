package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Position;

import java.util.Iterator;
import java.util.stream.Collectors;

public class TypeParameters extends Entity
        implements Iterable<TypeParameter> {
    private IdentifierMap<TypeParameter> params;

    public TypeParameters(Position pos,
                          IdentifierMap<TypeParameter> params) {
        super(pos);
        this.params = params;
    }

    public IdentifierMap<TypeParameter> params() {
        return params;
    }

    public TypeParameter get(int i) {
        return params.getValue(i);
    }

    public int size() {
        return params.size();
    }

    public boolean isEmpty() {
        return params.isEmpty();
    }

    public Iterator<TypeParameter> iterator() {
        return params.iterator();
    }

    //

    @Override
    public String toString() {
        if (params.isEmpty()) return "";
        return '`' + params.stream().map(Object::toString)
                .collect(Collectors.joining(", "))
                + '`';
    }

    //

    public static TypeParameters empty() {
        return new TypeParameters(Position.ZERO, new IdentifierMap<>());
    }

}
