package org.cossbow.feng.ast.dcl;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.util.Optional;

/**
 * Declaration types of variables, parameters, and fields.
 * <p>
 * Exsample declaration with type {@code int}:
 * <p>
 * variable {@code var a int}.
 * <p>
 * parameter {@code f(a int)}.
 * <p>
 * field {@code struct A { a int; }}.
 */
abstract
public class TypeDeclarer extends Entity {
    public TypeDeclarer(Position pos) {
        super(pos);
    }

    /**
     * Compare the base type:
     * <p>
     * 1. For a reference type, its base type is its dereference type.
     * Base type of {@code *int} is {@code int}
     * <p>
     * 2. For array types, the base type is its element type.
     * Base type of {@code [2]int} is {@code int}
     * <p>
     * 3. For other types, the base type is itself.
     *
     */
    public boolean baseTypeSame(TypeDeclarer td) {
        return true;
    }

    /**
     * Compare two types that are completely identical.
     */
    abstract public boolean equals(Object obj);

    /**
     * Can used for HashMap, see {@link #equals(Object)}
     */
    abstract public int hashCode();

    /**
     * Try get the {@link Refer} of this type.
     */
    public Optional<Refer> maybeRefer() {
        if (this instanceof Referable r)
            return r.refer();
        return Optional.empty();
    }

    /**
     * Return the dereference type, used for dereference-operation:
     * <p>
     * {@code var r *int = new(int); var c int = *r; }
     */
    public Optional<TypeDeclarer> derefer() {
        return Optional.empty();
    }

    /**
     * The {@code size} of mappable type can be calculated at compile time.
     * <p>
     * The mappable type is: int, float, struct, union and the fixed array of them
     */
    private Long size;

    public void size(long size) {
        this.size = size;
    }

    public Long size() {
        return size;
    }

    //

    public boolean isNil() {
        return false;
    }

    public boolean isBool() {
        return false;
    }

    public boolean isInteger() {
        return false;
    }

    public boolean isVoid() {
        return false;
    }

    /**
     * check required
     * 1. required attrubite of reference
     * 2. true if it's value-type
     */
    public boolean required() {
        var r = maybeRefer();
        // value-type is required
        return r.none() || r.get().required();
    }

    /**
     * check if this type contains type-paramster:
     * <p>
     * If {@code T} is type-parameter in this context, a type with
     * type-var is like: {@code T}, {@code List`T`}, etc.
     */
    public boolean hasTypeVar() {
        return false;
    }
}
