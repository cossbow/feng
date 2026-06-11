package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.IdentifierMap;
import org.cossbow.feng.ast.Symbol;

import java.util.Set;

import static org.cossbow.feng.ast.Position.ZERO;

final
public class BuiltinClasses {

    public static final Set<ClassDefinition> All = Set.of(
            ClassDefinition.ObjectClass,
            new ClassDefinition(ZERO, new Symbol(
                    new Identifier("NilPointer")),
                    new IdentifierMap<>()),
            new ClassDefinition(ZERO, new Symbol(
                    new Identifier("OutOfBounds")),
                    new IdentifierMap<>())
    );

    static {
        for (var cd : All) cd.builtin(true);
    }
}
