package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import static org.cossbow.feng.ast.Position.*;

public class EnumDefinition extends TypeDefinition {
    private IdentifierMap<Value> values;

    public EnumDefinition(Position pos,
                          Modifier modifier,
                          Symbol name,
                          IdentifierMap<Value> values) {
        super(pos, modifier, name, TypeParameters.empty(),
                TypeDomain.ENUM);
        this.values = values;
    }

    public IdentifierMap<Value> values() {
        return values;
    }

    public int size() {
        return values.size();
    }

    public Value ofId(int id) {
        return values.getValue(id);
    }

    public boolean newable() {
        return true;
    }


    public static final class Value extends Entity {
        private final int id;
        private final Identifier name;
        private final Lazy<Expression> init;
        private final StringLiteral nameLit;

        public Value(Position pos,
                     int id,
                     Identifier name,
                     Optional<Expression> init,
                     StringLiteral nameLit) {
            super(pos);
            this.id = id;
            this.name = name;
            this.init = Lazy.of(init);
            this.nameLit = nameLit;
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

        public StringLiteral nameLit() {
            return nameLit;
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

    public final EnumField IdField = makeField(TokenFieldId,
            Primitive.INT.declarer(ZERO), false);
    public final EnumField ValueField = makeField(TokenFieldValue,
            Primitive.INT.declarer(ZERO), false);
    public final EnumField NameField = makeField(TokenFieldName,
            new ArrayTypeDeclarer(ZERO, Primitive.BYTE.declarer(pos()),
                    Optional.empty(),
                    Optional.of(new Refer(ZERO, ReferKind.STRONG,
                            true, true))),
            true);

    private EnumField makeField(String name, TypeDeclarer td, boolean enablePhantom) {
        return new EnumField(new Identifier(pos(), name), td, enablePhantom);
    }

    public class EnumField extends Field {
        private final boolean enablePhantom;

        public EnumField(Identifier name,
                         TypeDeclarer type,
                         boolean enablePhantom) {
            super(EnumDefinition.this.pos(), name, type);
            this.enablePhantom = enablePhantom;
        }

        public boolean unmodifiable() {
            return true;
        }

        public boolean enablePhantom() {
            return enablePhantom;
        }
    }

}
