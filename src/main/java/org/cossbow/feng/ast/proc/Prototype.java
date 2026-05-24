package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.VoidTypeDeclarer;
import org.cossbow.feng.util.Optional;

/**
 * The prototype of a procedure includes a parameter table
 * declaration and a return value declaration.
 * <p>
 * All callable programs have a prototype, including functions,
 * methods, and variables of function types
 */
public class Prototype extends Entity {
    private final ParameterSet parameterSet;
    private Optional<TypeDeclarer> returnSet;

    public Prototype(Position pos,
                     ParameterSet parameterSet,
                     Optional<TypeDeclarer> returnSet) {
        super(pos);
        this.parameterSet = parameterSet;
        this.returnSet = returnSet;
    }

    public Prototype(Position pos,
                     ParameterSet parameterSet,
                     TypeDeclarer returnSet) {
        this(pos, parameterSet, Optional.of(returnSet));
    }

    public Prototype(Position pos,
                     ParameterSet parameterSet) {
        this(pos, parameterSet, Optional.empty());
    }

    public ParameterSet parameterSet() {
        return parameterSet;
    }

    public Optional<TypeDeclarer> returnSet() {
        return returnSet;
    }

    public void returnSet(Optional<TypeDeclarer> returnSet) {
        this.returnSet = returnSet;
    }

    public TypeDeclarer returnType() {
        return returnSet.getOrElse(new VoidTypeDeclarer(pos()));
    }

    /**
     * check if this type contains type-paramster:
     * <p>
     * If {@code T} is type-parameter in this context, a type with
     * type-var is like: {@code T}, {@code List`T`}, etc.
     */
    public boolean hasTypeVar() {
        return parameterSet.hasTypeVar() ||
                returnSet.match(TypeDeclarer::hasTypeVar);
    }

    //

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Prototype p)) return false;
        return parameterSet.equals(p.parameterSet) &&
                returnSet.equals(p.returnSet);
    }

    @Override
    public int hashCode() {
        int result = parameterSet.hashCode();
        result = 31 * result + returnSet.hashCode();
        return result;
    }

    //
    @Override
    public String toString() {
        if (returnSet.none())
            return "(" + parameterSet + ") ";
        return "(" + parameterSet + ") " + returnSet.get();
    }
}
