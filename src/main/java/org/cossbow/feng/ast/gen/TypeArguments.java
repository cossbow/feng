package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeArguments extends Entity
        implements Iterable<TypeDeclarer> {
    private final List<TypeDeclarer> arguments;

    public TypeArguments(Position pos,
                         List<TypeDeclarer> arguments) {
        super(pos);
        this.arguments = arguments;
    }

    public List<TypeDeclarer> arguments() {
        return arguments;
    }

    public int size() {
        return arguments.size();
    }

    public TypeDeclarer get(int i) {
        return arguments.get(i);
    }

    public boolean isEmpty() {
        return arguments.isEmpty();
    }

    public Stream<TypeDeclarer> stream() {
        return arguments.stream();
    }

    public Iterator<TypeDeclarer> iterator() {
        return arguments.iterator();
    }

    public TypeArguments map(Function<TypeDeclarer, TypeDeclarer> f) {
        var list = new ArrayList<TypeDeclarer>(arguments.size());
        for (var a : arguments) {
            list.add(f.apply(a));
        }
        return new TypeArguments(pos(), list);
    }

    public boolean hasTemplate() {
        return arguments.stream().anyMatch(
                TypeDeclarer::hasTemplate);
    }

    //

    @Override
    public boolean equals(Object o) {
        return o instanceof TypeArguments t &&
                arguments.equals(t.arguments);
    }

    @Override
    public int hashCode() {
        return arguments.hashCode();
    }

    //

    @Override
    public String toString() {
        if (arguments.isEmpty()) return "";
        return '`' + arguments.stream().map(Object::toString)
                .collect(Collectors.joining(", "))
                + '`';
    }

    //

    public static final TypeArguments EMPTY = new TypeArguments(Position.ZERO, List.of());
}
