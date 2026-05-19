package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.*;
import org.cossbow.feng.ast.expr.Expression;
import org.cossbow.feng.ast.gen.TypeParameters;
import org.cossbow.feng.ast.lit.IntegerLiteral;
import org.cossbow.feng.ast.oop.ClassMethod;
import org.cossbow.feng.ast.proc.ParameterSet;
import org.cossbow.feng.ast.proc.Prototype;
import org.cossbow.feng.util.ErrorUtil;
import org.cossbow.feng.util.Optional;

import java.util.List;
import java.util.Map;

import static org.cossbow.feng.ast.Position.ZERO;

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

    public static final ArrayField FieldLength = new ArrayField(ZERO,
            new Identifier("length"),
            Primitive.INT.declarer());

    public static class ArrayField extends Field {
        private ArrayField(Position pos,
                           Identifier name,
                           TypeDeclarer type) {
            super(pos, name, type);
        }

        public boolean unmodifiable() {
            return true;
        }

        public boolean enablePhantom() {
            return false;
        }
    }

    //

    static final PrimitiveTypeDeclarer PARAM_INT = Primitive.INT.declarer();
    static final PrimitiveTypeDeclarer PARAM_BOOL = Primitive.BOOL.declarer();

    static final ArrayMethod MethodSwap = new ArrayMethod("swap",
            ParameterSet.anon(List.of(PARAM_INT, PARAM_INT)), false);
    static final ArrayMethod MethodMove = new ArrayMethod("move",
            ParameterSet.anon(List.of(PARAM_INT, PARAM_INT)), false);

    public static class ArrayMethod extends Method {
        private final Identifier name;
        private final Prototype prototype;
        private final boolean unmodifiable;

        public ArrayMethod(String name,
                           ParameterSet parameterSet,
                           TypeDeclarer returnSet,
                           boolean unmodifiable) {
            super(ZERO);
            this.name = new Identifier(name);
            this.prototype = new Prototype(ZERO, parameterSet, returnSet);
            this.unmodifiable = unmodifiable;
        }

        public ArrayMethod(String name,
                           ParameterSet parameterSet,
                           boolean unmodifiable) {
            super(ZERO);
            this.name = new Identifier(name);
            this.prototype = new Prototype(ZERO, parameterSet);
            this.unmodifiable = unmodifiable;
        }

        @Override
        public Identifier name() {
            return name;
        }

        @Override
        public Prototype prototype() {
            return prototype;
        }

        @Override
        public TypeParameters generic() {
            return TypeParameters.empty();
        }

        @Override
        public TypeDefinition master() {
            return ErrorUtil.unreachable();
        }

        @Override
        public List<ClassMethod> override() {
            return List.of();
        }

        @Override
        public boolean unmodifiable() {
            return unmodifiable;
        }
    }

    static final Map<Identifier, ArrayMethod> METHODS = Map.of(
            MethodSwap.name, MethodSwap,
            MethodMove.name, MethodMove
    );

    public static Optional<ArrayMethod> methodOf(Identifier name) {
        return Optional.of(METHODS.get(name));
    }

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
