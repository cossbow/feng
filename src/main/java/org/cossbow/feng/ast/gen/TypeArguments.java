package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Optional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TypeArguments extends Entity {
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

    public Optional<TypeDeclarer> tryGet(int i) {
        return isEmpty() ? Optional.empty() : Optional.of(get(i));
    }

    public boolean isEmpty() {
        return arguments.isEmpty();
    }

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
