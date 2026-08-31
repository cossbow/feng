package org.cossbow.feng.ast.type;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

/**
 * Generic type parameter constraint, expressed as a {@link TypeConstraint}.
 * <p>
 * A constraint is a set of types: {@link #contains(TypeDeclarer)} answers
 * whether a given type satisfies the constraint.
 * <p>
 * {@code class Stack<T: *any>} — the constraint is {@code *any}
 * (all reference types).
 */
abstract
public class TypeConstraint extends Entity {
    public TypeConstraint(Position pos) {
        super(pos);
    }

    /**
     * Whether the given type satisfies this constraint.
     */
    abstract public boolean contains(TypeDeclarer t);

    /**
     * A view of the constraint as a partial type. Actual types with
     * additional information can be produced by the analysis phase.
     */
    abstract public TypeView view();

    //

    abstract public boolean equals(Object obj);

    abstract public int hashCode();

}
