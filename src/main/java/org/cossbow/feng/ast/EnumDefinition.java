package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.MemType;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.util.ErrorUtil;
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
        private final int id;
        private final Identifier name;
        private final Lazy<Expression> init;

        public Value(Position pos,
                     int id,
                     Identifier name,
                     Optional<Expression> init) {
            super(pos);
            this.id = id;
            this.name = name;
            this.init = Lazy.of(init);
        }

        public int id() {
            return id;
        }

        public Identifier name() {
            return name;
        }

        public Lazy<Expression> init() {
            return init;
        }

        private volatile int val;

        public int val() {
            return val;
        }

        public void val(int v) {
            this.val = v;
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


        //

        @Override
        public String toString() {
            return "Value[" +
                    "name=" + name + ", " +
                    "init=" + init + ']';
        }

    }

    //

    public Optional<EnumField> getField(Identifier name) {
        var td = switch (name.value()) {
            case "id", "value" -> Primitive.INT.declarer(name.pos());
            case "name" -> new MemTypeDeclarer(pos(), new MemType(pos(), true,
                    Optional.empty()), new Refer(pos(),
                    ReferKind.STRONG, false, false));
            case null, default -> null;
        };
        if (td == null) return Optional.empty();
        return Optional.of(new EnumField(pos(), name, td, this));
    }

    public static class EnumField extends Field {
        private final EnumDefinition definition;

        public EnumField(Position pos,
                         Identifier name,
                         TypeDeclarer type,
                         EnumDefinition definition) {
            super(pos, name, type);
            this.definition = definition;
        }

        public EnumDefinition definition() {
            return definition;
        }
    }

}
