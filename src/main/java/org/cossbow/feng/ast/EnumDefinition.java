package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.LiteralTypeDeclarer;
import org.cossbow.feng.ast.dcl.Primitive;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.lit.Literal;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import java.util.function.Function;

import static java.nio.charset.StandardCharsets.US_ASCII;

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

    public Value ofId(int id) {
        return values.getValue(id);
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
        public boolean equals(Object o) {
            if (!(o instanceof Value t))
                return false;
            return name.equals(t.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
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

    public Optional<Function<Value, Literal>> field(Identifier name) {
        Function<Value, Literal> f = switch (name.value()) {
            case "id" -> v -> new IntegerLiteral(v.pos(), v.id);
            case "value" -> v -> new IntegerLiteral(v.pos(), v.val);
            case "name" -> v -> new StringLiteral(v.pos(),
                    US_ASCII, v.name.value().getBytes());
            case null, default -> null;
        };
        return Optional.of(f);
    }

    //

    public static final String TokenFieldId = "id";
    public static final String TokenFieldValue = "value";
    public static final String TokenFieldName = "name";

    public Optional<EnumField> getField(Identifier name) {
        var f = switch (name.value()) {
            case TokenFieldId -> IdField;
            case TokenFieldValue -> ValueField;
            case TokenFieldName -> NameField;
            case null, default -> null;
        };
        return Optional.of(f);
    }

    private final EnumField IdField = makeField(TokenFieldId, Primitive.INT.declarer(pos()));
    private final EnumField ValueField = makeField(TokenFieldValue, Primitive.INT.declarer(pos()));
    private final EnumField NameField = makeField(TokenFieldName, new LiteralTypeDeclarer(pos(),
            new StringLiteral(pos(), US_ASCII, TokenFieldName.getBytes(US_ASCII))));

    private EnumField makeField(String name, TypeDeclarer td) {
        return new EnumField(pos(), new Identifier(pos(), name), td, this);
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

    }

}
