package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Position;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tuple is anonymous composite data type, and it's value type.
 * <p>
 * The tuple {@code (int,bool,A,*A)} can contain 4 elements,
 * and each element is int, bool, A, and *A in.
 * <p>
 * A tuple must have at least two elements.
 */
public class TupleTypeDeclarer extends TypeDeclarer {
    /**
     * The types of elements
     */
    private final List<TypeDeclarer> elements;

    public TupleTypeDeclarer(Position pos,
                             List<TypeDeclarer> elements) {
        super(pos);
        this.elements = elements;
    }

    public List<TypeDeclarer> elements() {
        return elements;
    }

    public TypeDeclarer get(int i) {
        return elements.get(i);
    }

    //

    @Override
    public boolean equals(Object o) {
        return o instanceof TupleTypeDeclarer t &&
                elements.equals(t.elements);
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }

    //
    @Override
    public String toString() {
        return elements.stream().map(Object::toString)
                .collect(Collectors.joining(
                        ",", "(", ")"));
    }
}
