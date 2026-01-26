package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.util.Optional;

import java.math.BigInteger;
import java.util.Objects;

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

    public void length(Optional<Expression> length) {
        this.length = length;
    }

    public Optional<Refer> refer() {
        return refer;
    }

    public boolean literal() {
        return literal;
    }

    //

    private Long len;

    public long len() {
        return len;
    }

    public void len(long len) {
        this.len = len;
    }

    public boolean isEmpty() {
        return len == null || len == 0;
    }

    //

    public Optional<ArrayField> getField(Identifier name) {
        if (LengthField.name().equals(name))
            return Optional.of(LengthField);

        return Optional.empty();
    }

    public final ArrayField LengthField = new ArrayField(pos(),
            new Identifier(pos(), "length"),
            Primitive.INT.declarer(pos()), this);

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


    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArrayTypeDeclarer t))
            return false;
        return element.equals(t.element) &&
                (literal || Objects.equals(len, t.len)) &&
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
