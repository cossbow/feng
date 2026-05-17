package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Field;
import org.cossbow.feng.ast.Identifier;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.util.Optional;

/**
 * Array type:
 * <p>
 * If fixed the length, array will be value-type: {@code [2]int}, {@code [2][4]int}
 * <p>
 * It will be array-reference if no length: {@code [*]int}, {@code [&]int}
 */
public class ArrayTypeDeclarer extends TypeDeclarer
        implements Referable {
    private TypeDeclarer element;
    private Optional<Expression> length;
    /**
     * If not set {@code length}, must set the {@code refer}
     */
    private Optional<Refer> refer;
    /**
     * True if the type is inferred from array-literal
     */
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

    public void element(TypeDeclarer element) {
        this.element = element;
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

    /**
     * cache the fixed length
     */
    private Long len;
    /**
     * cache the element size, if element type was mappable
     */
    private Long unit;

    public Long len() {
        return len;
    }

    public void len(long len) {
        this.len = len;
    }

    public Long unit() {
        return unit;
    }

    public void unit(Long unit) {
        this.unit = unit;
    }

    public boolean hasTypeVar() {
        return element.hasTypeVar();
    }

    //

    public static ArrayTypeDeclarer make(
            TypeDeclarer et, int len, Position pos) {
        var l = new IntegerLiteral(pos, len).expr();
        var t = new ArrayTypeDeclarer(pos, et,
                Optional.of(l), Optional.empty(), true);
        t.len(len);
        return t;
    }

    public static ArrayTypeDeclarer make(
            TypeDeclarer et, Optional<Refer> r, Position pos) {
        return new ArrayTypeDeclarer(pos, et, Optional.empty(),
                r, false);
    }

    public Optional<ArrayField> getField(Identifier name) {
        if (LengthField.name().equals(name))
            return Optional.of(LengthField);

        return Optional.empty();
    }

    public final ArrayField LengthField = new ArrayField(pos(),
            new Identifier(pos(), FIELD_LENGTH),
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

        public boolean unmodifiable() {
            return true;
        }

        public boolean enablePhantom() {
            return false;
        }
    }

    public static final String FIELD_LENGTH = "length";

    //

    public boolean baseTypeSame(TypeDeclarer td) {
        if (!(td instanceof ArrayTypeDeclarer t))
            return false;
        return element.equals(t.element);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArrayTypeDeclarer t))
            return false;
        return element.equals(t.element) && (refer.none()
                ? len.equals(t.len)
                : refer.equals(t.refer));
    }

    @Override
    public int hashCode() {
        int result = element.hashCode();
        if (refer.none())
            result = 31 * result + len.hashCode();
        else
            result = 31 * result + refer.hashCode();
        return result;
    }

    //

    @Override
    public String toString() {
        if (refer.has())
            return "[" + refer.get() + "]" + element;
        if (length.has())
            return "[" + len + "]" + element;
        return "[]" + element;
    }

}
