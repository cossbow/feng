package org.cossbow.feng.ast.attr;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.lit.*;
import org.cossbow.feng.util.Optional;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;

import java.util.Map;

public class AttributeField extends Entity {
    private Identifier name;
    private Type type;
    private boolean array;
    private Optional<Expression> init;

    public AttributeField(Position pos,
                          Identifier name,
                          Type type,
                          boolean array,
                          Optional<Expression> init) {
        super(pos);
        this.name = name;
        this.type = type;
        this.array = array;
        this.init = init;
    }

    public Identifier name() {
        return name;
    }

    public Type type() {
        return type;
    }

    public boolean array() {
        return array;
    }

    public Optional<Expression> init() {
        return init;
    }

    //
    public enum Type {
        INT("int", IntegerLiteral.class),
        FLOAT("float", FloatLiteral.class),
        BOOL("bool", BoolLiteral.class),
        STRING("string", StringLiteral.class),
        ;
        public final String symbol;
        public final Class<? extends Literal> literal;

        Type(String symbol, Class<? extends Literal> literal) {
            this.symbol = symbol;
            this.literal = literal;
        }

        public String toString() {
            return symbol;
        }
    }

    public static final Map<String, Type> TYPE_MAP = Map.of(
            Type.INT.symbol, Type.INT,
            Type.FLOAT.symbol, Type.FLOAT,
            Type.BOOL.symbol, Type.BOOL,
            Type.STRING.symbol, Type.STRING
    );

}
