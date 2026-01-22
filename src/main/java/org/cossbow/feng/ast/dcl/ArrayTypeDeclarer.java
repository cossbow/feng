package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Optional;

import java.math.BigInteger;
import java.util.Map;

public class ArrayTypeDeclarer extends TypeDeclarer
        implements Referable {
    private TypeDeclarer element;
    private Optional<Expression> length;
    private Optional<Refer> refer;
    private boolean literal;

    public ArrayTypeDeclarer(Position pos,
                             TypeDeclarer element,
                             Optional<Expression> length,
                             Optional<Refer> refer,
                             boolean literal) {
        super(pos);
        this.element = element;
        this.length = length;
        this.refer = refer;
        this.literal = literal;
    }

    public ArrayTypeDeclarer(Position pos,
                             TypeDeclarer element,
                             Optional<Expression> length,
                             Optional<Refer> refer) {
        this(pos, element, length, refer, false);
    }

    public TypeDeclarer element() {
        return element;
    }

    public Optional<Expression> length() {
        return length;
    }

    public Optional<Refer> refer() {
        return refer;
    }

    public boolean literal() {
        return literal;
    }

    //

    private volatile BigInteger lenValue;

    public BigInteger lenValue() {
        return lenValue;
    }

    public void lenValue(BigInteger lv) {
        this.lenValue = lv;
    }

    //

    public Optional<ArrayField> getField(Identifier name) {
        var primitive = innerFields.get(name.value());
        if (primitive == null) return Optional.empty();

        return Optional.of(new ArrayField(pos(), name,
                primitive.declarer(pos()), this));
    }

    public static class ArrayField extends Field {
        private final ArrayTypeDeclarer master;

        private ArrayField(Position pos,
                           Identifier name,
                           TypeDeclarer type,
                           ArrayTypeDeclarer master) {
            super(pos, name, type);
            this.master = master;
        }

        public ArrayTypeDeclarer master() {
            return master;
        }
    }

    static final Map<String, Primitive> innerFields = Map.of(
            "length", Primitive.INT
    );

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArrayTypeDeclarer t))
            return false;
        return element.equals(t.element) &&
                length.equals(t.length) &&
                refer.equals(t.refer);
    }

    @Override
    public int hashCode() {
        int result = element.hashCode();
        result = 31 * result + length.hashCode();
        result = 31 * result + refer.hashCode();
        return result;
    }

    //

    @Override
    public String toString() {
        if (refer.has())
            return "[" + refer.get() + "]" + element;
        if (length.has())
            return "[" + length.get() + "]" + element;
        return "[]" + element;
    }

}
