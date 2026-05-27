package org.cossbow.feng.ast.expr;

import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TupleTypeDeclarer;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.util.Optional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Used to initialize elements of tuple types, each element can mark type.
 * The type of this expression is {@link TupleTypeDeclarer}
 * <p>
 * Without type: {@code (100,3.14,true,nil)}
 * <p>
 * With element types:
 * {@code (100:uint,3.14:float32,true:bool,nil:*A)}
 * <p>
 * Or: {@code (100:uint,3.14,true,nil)}
 */
public class TupleExpression extends PrimaryExpression {
    /**
     * The element values
     */
    private final List<Expression> elements;
    /**
     * Marked types: {@code Optional} empty indicates no type marked.
     * <p>
     * This size is aligned to {@link #elements}
     */
    private final List<Optional<TypeDeclarer>> types;

    public TupleExpression(Position pos,
                           List<Expression> elements,
                           List<Optional<TypeDeclarer>> types) {
        super(pos);
        this.elements = elements;
        this.types = types;
    }

    public List<Expression> elements() {
        return elements;
    }

    public List<Optional<TypeDeclarer>> types() {
        return types;
    }


    //
    @Override
    public String toString() {
        return elements.stream().map(Expression::toString)
                .collect(Collectors.joining(",", "(", ")"));
    }
}
