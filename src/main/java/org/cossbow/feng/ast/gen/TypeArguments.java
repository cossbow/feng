package org.cossbow.feng.ast.gen;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.List;

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

    public boolean isEmpty() {
        return arguments.isEmpty();
    }

    //

    public static final TypeArguments EMPTY = new TypeArguments(Position.ZERO, List.of());
}
