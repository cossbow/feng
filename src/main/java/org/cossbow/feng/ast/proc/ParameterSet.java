package org.cossbow.feng.ast.proc;


import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

public class ParameterSet {

    public List<TypeDeclarer> types() {
        return List.of();
    }

    public TypeDeclarer getType(int i) {
        throw new NoSuchElementException();
    }

    public int size() {
        return 0;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    final
    public boolean equals(Object o) {
        if (!(o instanceof ParameterSet ps))
            return false;

        var size = size();
        if (size != ps.size())
            return false;

        for (int i = 0; i < size; i++)
            if (!getType(i).equals(ps.getType(i)))
                return false;

        return true;
    }

    @Override
    final
    public int hashCode() {
        return types().hashCode();
    }

    //


    @Override
    public String toString() {
        return types().stream().map(Object::toString)
                .collect(Collectors.joining(", "));
    }
}
