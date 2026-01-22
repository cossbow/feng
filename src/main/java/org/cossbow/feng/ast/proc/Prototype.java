package org.cossbow.feng.ast.proc;

import org.cossbow.feng.ast.Entity;
import org.cossbow.feng.ast.Position;
import org.cossbow.feng.ast.dcl.TypeDeclarer;

import java.util.List;
import java.util.Objects;

public class Prototype extends Entity {
    private ParameterSet parameterSet;
    private List<TypeDeclarer> returnSet;

    public Prototype(Position pos,
                     ParameterSet parameterSet,
                     List<TypeDeclarer> returnSet) {
        super(pos);
        this.parameterSet = parameterSet;
        this.returnSet = returnSet;
    }

    public ParameterSet parameterSet() {
        return parameterSet;
    }

    public List<TypeDeclarer> returnSet() {
        return returnSet;
    }

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
        return "(" + parameterSet + ") " + returnSet;
    }
}
