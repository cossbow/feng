package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;
import org.cossbow.feng.ast.dcl.VoidTypeDeclarer;
import org.cossbow.feng.util.Optional;

public class Prototype extends Entity {
    private ParameterSet parameterSet;
    private Optional<TypeDeclarer> returnSet;

    public Prototype(Position pos,
                     ParameterSet parameterSet,
                     Optional<TypeDeclarer> returnSet) {
        super(pos);
        this.parameterSet = parameterSet;
        this.returnSet = returnSet;
    }

    public ParameterSet parameterSet() {
        return parameterSet;
    }

    public void parameterSet(ParameterSet parameterSet) {
        this.parameterSet = parameterSet;
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

    public boolean hasTemplate() {
        return parameterSet.hasTemplate() ||
                returnSet.match(TypeDeclarer::hasTemplate);
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
