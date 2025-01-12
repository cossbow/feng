package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.TypeDefinition;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.TypeParameters;

import java.util.List;
import java.util.Optional;

public class EnumDefinition extends TypeDefinition {
    private final List<Value> values;

    public EnumDefinition(Position pos,
                          Modifier modifier,
                          Identifier name,
                          List<Value> values) {
        super(pos, modifier, Optional.of(name), TypeParameters.EMPTY);
        this.values = values;
    }

    public List<Value> values() {
        return values;
    }

    public record Value(Identifier name, Optional<Expression> init) {
    }
}
