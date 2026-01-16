package org.cossbow.feng.ast.oop;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.Objects;

public class EnumDefinition extends TypeDefinition {
    private IdentifierTable<Value> values;

    public EnumDefinition(Position pos,
                          Modifier modifier,
                          Symbol name,
                          IdentifierTable<Value> values) {
        super(pos, modifier, name, TypeParameters.empty(),
                TypeDomain.ENUM);
        this.values = values;
    }

    public IdentifierTable<Value> values() {
        return values;
    }

    public static final class Value extends Entity {
        private final Identifier name;
        private final Lazy<Expression> init;

        public Value(Position pos,
                     Identifier name,
                     Optional<Expression> init) {
            super(pos);
            this.name = name;
            this.init = Lazy.of(init);
        }

        public Identifier name() {
            return name;
        }

        public Lazy<Expression> init() {
            return init;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Value) obj;
            return Objects.equals(this.name, that.name) &&
                    Objects.equals(this.init, that.init);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, init);
        }

        @Override
        public String toString() {
            return "Value[" +
                    "name=" + name + ", " +
                    "init=" + init + ']';
        }

    }
}
