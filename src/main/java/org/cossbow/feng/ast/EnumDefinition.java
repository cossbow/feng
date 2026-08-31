package org.cossbow.feng.ast;

import org.cossbow.feng.ast.attr.Modifier;
import org.cossbow.feng.ast.dcl.*;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.StringLiteral;
import org.cossbow.feng.util.Lazy;
import org.cossbow.feng.util.Optional;

import static org.cossbow.feng.ast.Position.*;

/**
 * An enum is defined such that its domain is strictly limited
 * to a finite set of values.
 * <p>
 * {@code enum TaskState {WAIT, RUN, DONE,}}
 * <p>
 * {@code enum TaskState {WAIT, RUN=100, DONE,}}
 */
public class EnumDefinition extends TypeDefinition
        implements Aggregatable<EnumDefinition.EnumField> {
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
        /**
         * The ID of the value is defined in order and is
         * automatically generated
         */
        private final int id;
        /**
         * The name of the enum value definition is also
         * the symbol that references the value
         */
        private final Identifier name;
        /**
         * Allow setting a constant or constant expression
         * bound to an enum value.
         * <p>
         * Default equals {@link Value#id}.
         */
        private final Lazy<Expression> init;
        /**
         * Literal of {@link Value#name}.
         */
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

    public static final Identifier TokenFieldId = new Identifier("id");
    public static final Identifier TokenFieldValue = new Identifier("value");
    public static final Identifier TokenFieldName = new Identifier("name");

    public Optional<EnumField> field(Identifier name) {
        return fields.tryGet(name);
    }

    public IdentifierMap<EnumField> fields() {
        return fields;
    }

    private final IdentifierMap<EnumField> fields = new IdentifierMap<>();

    {
        fields.add(TokenFieldId, makeField(TokenFieldId,
                Primitive.INT.declarer(pos()), false));
        fields.add(TokenFieldValue, makeField(TokenFieldValue,
                Primitive.INT.declarer(pos()), false));
        fields.add(TokenFieldName, makeField(TokenFieldName,
                ArrayTypeDeclarer.make(Primitive.BYTE.declarer(pos()),
                        Optional.of(new Refer(pos(), ReferKind.STRONG,
                                true, true)),
                        pos()), true));
    }

    private EnumField makeField(Identifier name, TypeDeclarer td,
                                boolean enablePhantom) {
        return new EnumField(name, td, enablePhantom);
    }

    /**
     * Builtin fields of enum.
     */
    public class EnumField extends Field {
        private final Modifier modifier;
        private final boolean enablePhantom;

        public EnumField(Identifier name,
                         TypeDeclarer type,
                         boolean enablePhantom) {
            super(EnumDefinition.this.pos(), name, type);
            modifier = new Modifier(ZERO, true, new SymbolMap<>());
            this.enablePhantom = enablePhantom;
        }

        @Override
        public Modifier modifier() {
            return modifier;
        }

        public boolean immutable() {
            return true;
        }

        public boolean enablePhantom() {
            return enablePhantom;
        }
    }

}
