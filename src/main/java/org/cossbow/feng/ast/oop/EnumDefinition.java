package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.TypeParameters;

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
