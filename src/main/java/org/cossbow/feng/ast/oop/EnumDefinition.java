package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.UniqueTable;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.TypeParameters;

import java.util.Optional;

public class EnumDefinition extends TypeDefinition {
    private final UniqueTable<Value> values;

    public EnumDefinition(Position pos,
                          Modifier modifier,
                          Identifier name,
                          UniqueTable<Value> values) {
        super(pos, modifier, Optional.of(name), TypeParameters.empty());
        this.values = values;
    }

    public UniqueTable<Value> values() {
        return values;
    }

    public record Value(Identifier name, Optional<Expression> init) {
    }
}
